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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import mx.utng.deliverytrack.shared.config.ServerConfig
import mx.utng.deliverytrack.wear.data.WearableDataLayerHelper
import mx.utng.deliverytrack.wear.presentation.auth.WearCourierSession
import mx.utng.deliverytrack.wear.presentation.auth.WearLoginScreen
import mx.utng.deliverytrack.wear.presentation.pedidos.WearPedidoCardItem
import mx.utng.deliverytrack.wear.presentation.pedidos.WearPedidosCardsScreen
import mx.utng.deliverytrack.wear.presentation.theme.DeliveryTrackTheme
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Actividad principal del módulo Wear (WearOS).
 *
 * Se encarga de la inicialización de la Wearable Data Layer API, coordinar las pantallas
 * de flujo de usuario (Login, Listado de Pedidos Activos, Detalles del Pedido) y gestionar la
 * actualización de estados a través de peticiones HTTP directas al backend.
 */
class MainActivity : ComponentActivity() {

    private lateinit var dataLayerHelper: WearableDataLayerHelper

    // Estados reactivos que controlan la sesión y navegación del reloj
    private var activeCourier by mutableStateOf<WearCourierSession?>(null)
    private var selectedPedido by mutableStateOf<WearPedidoCardItem?>(null)

    // Estados reactivos que representan los datos del pedido en pantalla
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

    /**
     * Inicializa la actividad y la lógica de escucha del WearableDataLayerHelper.
     * Define la interfaz gráfica en Compose bajo el tema general.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se inicializa el puente de comunicación local con el dispositivo celular
        dataLayerHelper = WearableDataLayerHelper(
            context = this,
            onActiveOrderResponse = { success, data ->
                Log.d(TAG, "Active order response callback. Success=$success")
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

                    // Si llega un pedido con estatus 'Pendiente' (2), se genera alerta háptica
                    if (oldStatus == null && newStatus == 2) {
                        triggerHapticAlert("nuevo")
                    }
                } else {
                    resetOrderState()
                    statusMessage = ""
                }
            },
            onStatusUpdateResponse = { _, _ -> },
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

                    // Enrutamiento local de pantallas mediante variables de estado
                    if (courier == null) {
                        WearLoginScreen(
                            onLoginSuccess = { session ->
                                activeCourier = session
                            }
                        )
                    } else if (order == null) {
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
                                statusMessage = ""
                            },
                            onChangeCourierClick = {
                                activeCourier = null
                                selectedPedido = null
                            }
                        )
                    } else {
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
                            onAccept = { updateOrderStatusDirect(newStatus = 1) },
                            onReject = { updateOrderStatusDirect(newStatus = 4) },
                            onEnCamino = { updateOrderStatusDirect(newStatus = 3) },
                            onEntregado = { updateOrderStatusDirect(newStatus = 6) }
                        )
                    }
                }
            }
        }
    }

    /**
     * Envía la actualización de estado del pedido directamente a la API REST del backend
     * mediante HTTP PUT en un hilo de ejecución secundario para garantizar su recepción.
     *
     * @param newStatus Código numérico del nuevo estatus solicitado (ej: 1 = Aceptado, 3 = En camino, 6 = Entregado).
     */
    private fun updateOrderStatusDirect(newStatus: Int) {
        val id = activeOrderId ?: return
        isLoading = true
        statusMessage = ""

        thread {
            try {
                val body = JSONObject().apply {
                    put("estatus", newStatus)
                }.toString()

                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$id")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypassWear(conn)
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                val code = conn.responseCode

                runOnUiThread {
                    isLoading = false
                    if (code == 200) {
                        orderStatus = newStatus
                        statusMessage = when (newStatus) {
                            6 -> "¡Entrega completada!"
                            4 -> "Pedido cancelado"
                            else -> ""
                        }
                    } else {
                        val errText = try {
                            conn.errorStream?.bufferedReader()?.readText()
                        } catch (_: Exception) { null }
                        Log.e(TAG, "Status update failed. Code=$code Body=$errText")
                        statusMessage = "Error al actualizar (código $code)"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating order status directly", e)
                runOnUiThread {
                    isLoading = false
                    statusMessage = "Error de red: ${e.message}"
                }
            }
        }
    }

    /**
     * Limpia los estados locales de la entrega activa cargada en pantalla.
     */
    private fun resetOrderState() {
        activeOrderId = null
        clientName = ""
        addressText = ""
        orderDescription = ""
        orderStatus = null
    }

    /**
     * Genera un patrón de vibración (alerta háptica) en el reloj del repartidor
     * según el tipo de notificación recibida.
     *
     * @param type Tipo de alerta a disparar ("nuevo" para nuevo pedido, o "cancelado").
     */
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

    /**
     * Registra el callback de escucha de mensajes al iniciar el flujo de la aplicación.
     */
    override fun onStart() {
        super.onStart()
        dataLayerHelper.registerListener()
    }

    /**
     * Remueve el callback de escucha de mensajes al pausar o cerrar la aplicación.
     */
    override fun onStop() {
        super.onStop()
        dataLayerHelper.unregisterListener()
    }
}

/**
 * Función auxiliar para omitir las validaciones de cadenas SSL/TLS para pruebas locales HTTPS en la MainActivity.
 *
 * @param conn Instancia de conexión HTTP a la que se le aplicará el bypass.
 */
private fun applySslBypassWear(conn: java.net.HttpURLConnection) {
    if (conn is javax.net.ssl.HttpsURLConnection) {
        try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                }
            )
            val sc = javax.net.ssl.SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            conn.sslSocketFactory = sc.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }
}

/**
 * Composable que renderiza la pantalla de detalle de un pedido seleccionado en el WearOS.
 *
 * Muestra información clave del pedido (cliente, dirección, notas) y habilita un botón interactivo
 * contextual de acuerdo al estatus de la entrega, permitiendo cambiar el estatus mediante clics.
 *
 * @param activeOrderId Identificador único del pedido actual.
 * @param clientName Nombre del cliente receptor.
 * @param addressText Dirección de entrega con referencias.
 * @param orderDescription Observaciones del pedido.
 * @param orderStatus Código numérico del estatus del pedido.
 * @param isLoading Bandera de estado que desactiva botones y muestra spinner al actualizar.
 * @param statusMessage Mensaje descriptivo de éxito o error al realizar operaciones.
 * @param onBackToCardsList Callback invocado para regresar al listado general de entregas.
 * @param onAccept Callback para aceptar la entrega.
 * @param onReject Callback para rechazar la entrega.
 * @param onEnCamino Callback para marcar el pedido en camino.
 * @param onEntregado Callback para confirmar la entrega exitosa del pedido.
 */
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
            // Botón de regresar
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

            // Cabecera con ID de pedido
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

            // Spinner de carga activa
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

            // Mensajes informativos de estatus
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

            // Nombre de cliente
            item {
                Text(
                    text = clientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            // Ubicación del cliente
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

            // Observaciones del pedido
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

            // Fila de botones de acción contextuales según estatus del pedido
            item {
                when (orderStatus) {
                    2 -> { // Pendiente: Aceptar o Rechazar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = onReject,
                                enabled = !isLoading,
                                modifier = Modifier.weight(0.30f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text("Rechazar", fontSize = 10.sp)
                            }
                            Button(
                                onClick = onAccept,
                                enabled = !isLoading,
                                modifier = Modifier.weight(0.30f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                            ) {
                                Text("Aceptar", fontSize = 10.sp)
                            }
                        }
                    }
                    1 -> { // Aceptado: Iniciar ruta (En camino)
                        Button(
                            onClick = onEnCamino,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text("En camino", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    3, 5 -> { // En camino o Retrasado: Entregar
                        Button(
                            onClick = onEntregado,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                        ) {
                            Text("Entregado", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    6 -> { // Entregado: Indicación de completado
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