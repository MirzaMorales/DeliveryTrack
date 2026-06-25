package mx.utng.deliverytrack.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import mx.utng.deliverytrack.presentation.theme.DeliveryTrackTheme
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var dataLayerHelper: WearableDataLayerHelper

    // Active order state data parsed from JSON
    private var activeOrderId by mutableStateOf<Int?>(null)
    private var clientName by mutableStateOf("")
    private var addressText by mutableStateOf("")
    private var orderDescription by mutableStateOf("")
    private var orderStatus by mutableStateOf<Int?>(null)

    private var isLoading by mutableStateOf(false)
    private var statusMessage by mutableStateOf("")

    companion object {
        private const val TAG = "WearMainActivity"
        private const val REPARTIDOR_ID = 2 // Hardcoded repartidorId = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataLayerHelper = WearableDataLayerHelper(
            context = this,
            onActiveOrderResponse = { success, data ->
                Log.d(TAG, "Active order response callback. Success=$success")
                isLoading = false
                if (success && data != null) {
                    val id = data.optInt("id_pedido", -1)
                    activeOrderId = id
                    clientName = data.optString("nombre_cliente", "")
                    val dir = data.optString("direccion", "")
                    val ref = data.optString("referencia_lugar", "")
                    addressText = if (ref.isNotEmpty()) "$dir, $ref" else dir
                    orderDescription = data.optString("descripcion_pedido", "")
                    
                    val oldStatus = orderStatus
                    val newStatus = data.optInt("estatus", -1)
                    orderStatus = newStatus
                    statusMessage = ""

                    // If a new active order arrives and was not shown before, trigger vibration
                    if (oldStatus == null && newStatus == 2) {
                        triggerHapticAlert("nuevo")
                    }
                } else {
                    resetOrderState()
                    statusMessage = "Sin entregas activas"
                }
            },
            onStatusUpdateResponse = { success, data ->
                Log.d(TAG, "Status update response. Success=$success")
                isLoading = false
                if (success && data != null) {
                    val pedido = data.optJSONObject("pedido")
                    val newStatus = pedido?.optInt("estatus", -1) ?: -1
                    orderStatus = newStatus
                    statusMessage = ""
                    
                    // If transitioned to delivered (6) or canceled (4), reset
                    if (newStatus == 6 || newStatus == 4) {
                        resetOrderState()
                        statusMessage = if (newStatus == 6) "¡Entrega completada!" else "Pedido cancelado"
                    }
                } else {
                    statusMessage = "Error al actualizar"
                }
            },
            onHapticAlertReceived = { type ->
                Log.d(TAG, "Haptic alert notification received: $type")
                triggerHapticAlert(type)
                
                // If it was a cancellation alert, reset state and display message
                if (type.lowercase() == "cancelado") {
                    resetOrderState()
                    statusMessage = "Pedido cancelado por admin"
                } else if (type.lowercase() == "nuevo") {
                    // Refetch active order to show the newly assigned one
                    refreshOrder()
                }
            }
        )

        setContent {
            WearApp(
                activeOrderId = activeOrderId,
                clientName = clientName,
                addressText = addressText,
                orderDescription = orderDescription,
                orderStatus = orderStatus,
                isLoading = isLoading,
                statusMessage = statusMessage,
                onAccept = {
                    val id = activeOrderId
                    if (id != null) {
                        isLoading = true
                        dataLayerHelper.requestStatusUpdate(id, 1, REPARTIDOR_ID) // Accept = status 1
                    }
                },
                onReject = {
                    val id = activeOrderId
                    if (id != null) {
                        isLoading = true
                        dataLayerHelper.requestStatusUpdate(id, 4, REPARTIDOR_ID) // Reject = status 4 (Cancelado)
                    }
                },
                onEnCamino = {
                    val id = activeOrderId
                    if (id != null) {
                        isLoading = true
                        dataLayerHelper.requestStatusUpdate(id, 3, REPARTIDOR_ID) // En camino = status 3 (En ruta)
                    }
                },
                onEntregado = {
                    val id = activeOrderId
                    if (id != null) {
                        isLoading = true
                        dataLayerHelper.requestStatusUpdate(id, 6, REPARTIDOR_ID) // Entregado = status 6
                    }
                },
                onManualRefresh = {
                    refreshOrder()
                }
            )
        }
    }

    private fun resetOrderState() {
        activeOrderId = null
        clientName = ""
        addressText = ""
        orderDescription = ""
        orderStatus = null
    }

    private fun refreshOrder() {
        isLoading = true
        statusMessage = "Buscando pedido..."
        dataLayerHelper.requestActiveOrder(REPARTIDOR_ID)
    }

    private fun triggerHapticAlert(type: String) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = when (type.lowercase()) {
            "nuevo" -> longArrayOf(0, 150, 100, 150) // double short vibration
            "cancelado" -> longArrayOf(0, 300, 100, 100, 100, 100) // triple long warning
            else -> longArrayOf(0, 200)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    override fun onStart() {
        super.onStart()
        dataLayerHelper.registerListener()
    }

    override fun onResume() {
        super.onResume()
        // Auto-fetch active order on resume
        refreshOrder()
    }

    override fun onStop() {
        super.onStop()
        dataLayerHelper.unregisterListener()
    }
}

@Composable
fun WearApp(
    activeOrderId: Int?,
    clientName: String,
    addressText: String,
    orderDescription: String,
    orderStatus: Int?,
    isLoading: Boolean,
    statusMessage: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnCamino: () -> Unit,
    onEntregado: () -> Unit,
    onManualRefresh: () -> Unit
) {
    DeliveryTrackTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                    
                    // Header
                    item {
                        ListHeader(
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text(
                                text = "DeliveryTrack",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Loading State
                    if (isLoading) {
                        item {
                            Text(
                                text = "Cargando...",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                            )
                        }
                    }

                    // Status Messages (e.g. Success, Cancelled)
                    if (statusMessage.isNotEmpty()) {
                        item {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                        }
                    }

                    if (activeOrderId != null && orderStatus != null) {
                        // Order ID and Description
                        item {
                            Text(
                                text = "Pedido #${activeOrderId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Client Name
                        item {
                            Text(
                                text = clientName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                        }

                        // Concatenated Address - Large and highly readable
                        item {
                            Text(
                                text = addressText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 16.sp
                            )
                        }

                        // Description
                        if (orderDescription.isNotEmpty()) {
                            item {
                                Text(
                                    text = orderDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        // State-dependent sequential action buttons
                        item {
                            when (orderStatus) {
                                2 -> { // Pendiente: Show Accept & Reject side-by-side
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Reject (Red)
                                        Button(
                                            onClick = onReject,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFD32F2F),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Rechazar", fontSize = 11.sp)
                                        }
                                        // Accept (Green)
                                        Button(
                                            onClick = onAccept,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF388E3C),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Aceptar", fontSize = 11.sp)
                                        }
                                    }
                                }
                                1 -> { // Aceptado: Show "En camino" (Blue)
                                    Button(
                                        onClick = onEnCamino,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1976D2),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("En camino", fontSize = 13.sp)
                                    }
                                }
                                3, 5 -> { // En ruta or Retrasado: Show "Entregado" (Orange)
                                    Button(
                                        onClick = onEntregado,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF57C00),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Entregado", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    } else if (!isLoading) {
                        // Empty state: no active deliveries
                        item {
                            Text(
                                text = "Sin entregas activas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 15.dp)
                            )
                        }
                        item {
                            Text(
                                text = "Esperando asignaciones...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp)
                            )
                        }
                        item {
                            Button(
                                onClick = onManualRefresh,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            ) {
                                Text("Buscar Pedidos")
                            }
                        }
                    }
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun W1PreviewPending() {
    WearApp(
        activeOrderId = 104,
        clientName = "Juan Pérez",
        addressText = "Av. Juárez 123, Frente a Farmacia Guadalajara",
        orderDescription = "Pizza Familiar Pepperoni + Soda 2L",
        orderStatus = 2,
        isLoading = false,
        statusMessage = "",
        onAccept = {},
        onReject = {},
        onEnCamino = {},
        onEntregado = {},
        onManualRefresh = {}
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun W1PreviewAccepted() {
    WearApp(
        activeOrderId = 104,
        clientName = "Juan Pérez",
        addressText = "Av. Juárez 123, Frente a Farmacia Guadalajara",
        orderDescription = "Pizza Familiar Pepperoni + Soda 2L",
        orderStatus = 1,
        isLoading = false,
        statusMessage = "",
        onAccept = {},
        onReject = {},
        onEnCamino = {},
        onEntregado = {},
        onManualRefresh = {}
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun W1PreviewEmpty() {
    WearApp(
        activeOrderId = null,
        clientName = "",
        addressText = "",
        orderDescription = "",
        orderStatus = null,
        isLoading = false,
        statusMessage = "",
        onAccept = {},
        onReject = {},
        onEnCamino = {},
        onEntregado = {},
        onManualRefresh = {}
    )
}