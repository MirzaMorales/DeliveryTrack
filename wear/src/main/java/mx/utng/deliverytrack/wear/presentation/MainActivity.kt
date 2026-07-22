package mx.utng.deliverytrack.wear.presentation

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
import mx.utng.deliverytrack.wear.data.WearableDataLayerHelper
import mx.utng.deliverytrack.wear.presentation.auth.WearCourierSession
import mx.utng.deliverytrack.wear.presentation.auth.WearLoginScreen
import mx.utng.deliverytrack.wear.presentation.pedidos.WearPedidoCardItem
import mx.utng.deliverytrack.wear.presentation.pedidos.WearPedidosCardsScreen
import mx.utng.deliverytrack.wear.presentation.theme.DeliveryTrackTheme
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var dataLayerHelper: WearableDataLayerHelper

    private var activeCourier by mutableStateOf<WearCourierSession?>(null)
    private var selectedPedido by mutableStateOf<WearPedidoCardItem?>(null)

    private var activeOrderId by mutableStateOf<Int?>(null)
    private var clientName by mutableStateOf("")
    private var addressText by mutableStateOf("")
    private var orderDescription by mutableStateOf("")
    private var orderStatus by mutableStateOf<Int?>(null)

    private var isLoading by mutableStateOf(false)
    private var statusMessage by mutableStateOf("")

    companion object {
        private const val TAG = "WearMainActivity"
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

                    if (oldStatus == null && newStatus == 2) {
                        triggerHapticAlert("nuevo")
                    }
                } else {
                    resetOrderState()
                    statusMessage = ""
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
                    
                    if (newStatus == 6 || newStatus == 4) {
                        statusMessage = if (newStatus == 6) "¡Entrega completada!" else "Pedido cancelado"
                    }
                } else {
                    statusMessage = "Error al actualizar"
                }
            },
            onHapticAlertReceived = { type ->
                Log.d(TAG, "Haptic alert notification received: $type")
                triggerHapticAlert(type)
                
                if (type.lowercase() == "cancelado") {
                    statusMessage = "Pedido cancelado por admin"
                }
            }
        )

        setContent {
            DeliveryTrackTheme {
                AppScaffold {
                    val courier = activeCourier
                    val order = selectedPedido

                    if (courier == null) {
                        // 1. Wear Login Screen
                        WearLoginScreen(
                            onLoginSuccess = { session ->
                                activeCourier = session
                            }
                        )
                    } else if (order == null) {
                        // 2. Wear Order Cards List Screen
                        WearPedidosCardsScreen(
                            courierId = courier.idUser,
                            courierName = courier.nombreCompleto,
                            onPedidoCardClick = { cardItem ->
                                selectedPedido = cardItem
                                activeOrderId = cardItem.idPedido
                                clientName = cardItem.nombreCliente
                                addressText = cardItem.direccion
                                orderDescription = cardItem.descripcion
                                orderStatus = cardItem.estatus
                            },
                            onChangeCourierClick = {
                                activeCourier = null
                                selectedPedido = null
                            }
                        )
                    } else {
                        // 3. Wear Order Detail Screen + Back Button
                        WearOrderDetailScreen(
                            activeOrderId = activeOrderId,
                            clientName = clientName,
                            addressText = addressText,
                            orderDescription = orderDescription,
                            orderStatus = orderStatus,
                            isLoading = isLoading,
                            statusMessage = statusMessage,
                            onBackToCardsList = {
                                selectedPedido = null
                            },
                            onAccept = {
                                val id = activeOrderId
                                val repId = activeCourier?.idUser ?: 2
                                if (id != null) {
                                    isLoading = true
                                    dataLayerHelper.requestStatusUpdate(id, 1, repId)
                                    orderStatus = 1
                                }
                            },
                            onReject = {
                                val id = activeOrderId
                                val repId = activeCourier?.idUser ?: 2
                                if (id != null) {
                                    isLoading = true
                                    dataLayerHelper.requestStatusUpdate(id, 4, repId)
                                    orderStatus = 4
                                }
                            },
                            onEnCamino = {
                                val id = activeOrderId
                                val repId = activeCourier?.idUser ?: 2
                                if (id != null) {
                                    isLoading = true
                                    dataLayerHelper.requestStatusUpdate(id, 3, repId)
                                    orderStatus = 3
                                }
                            },
                            onEntregado = {
                                val id = activeOrderId
                                val repId = activeCourier?.idUser ?: 2
                                if (id != null) {
                                    isLoading = true
                                    dataLayerHelper.requestStatusUpdate(id, 6, repId)
                                    orderStatus = 6
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun resetOrderState() {
        activeOrderId = null
        clientName = ""
        addressText = ""
        orderDescription = ""
        orderStatus = null
    }

    private fun triggerHapticAlert(type: String) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = when (type.lowercase()) {
            "nuevo" -> longArrayOf(0, 150, 100, 150)
            "cancelado" -> longArrayOf(0, 300, 100, 100, 100, 100)
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

    override fun onStop() {
        super.onStop()
        dataLayerHelper.unregisterListener()
    }
}

@Composable
fun WearOrderDetailScreen(
    activeOrderId: Int?,
    clientName: String,
    addressText: String,
    orderDescription: String,
    orderStatus: Int?,
    isLoading: Boolean,
    statusMessage: String,
    onBackToCardsList: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnCamino: () -> Unit,
    onEntregado: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item {
                Button(
                    onClick = onBackToCardsList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 28.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33CBD5E1))
                ) {
                    Text(
                        text = "← Regresar",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(
                        text = "Pedido #${activeOrderId ?: ""}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }
            }

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

            item {
                Text(
                    text = clientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            item {
                Text(
                    text = "📍 $addressText",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

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

            item { Spacer(modifier = Modifier.height(6.dp)) }

            item {
                when (orderStatus) {
                    2 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = onReject,
                                modifier = Modifier.weight(0.30f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text("Rechazar", fontSize = 10.sp)
                            }
                            Button(
                                onClick = onAccept,
                                modifier = Modifier.weight(0.30f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                            ) {
                                Text("Aceptar", fontSize = 10.sp)
                            }
                        }
                    }
                    1 -> {
                        Button(
                            onClick = onEnCamino,
                            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text("En camino", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    3, 5 -> {
                        Button(
                            onClick = onEntregado,
                            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                        ) {
                            Text("Entregado", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    6 -> {
                        Text(
                            text = "✓ ¡Entrega completada!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
