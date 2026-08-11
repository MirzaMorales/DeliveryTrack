package mx.utng.deliverytrack.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/**
 * Clase de soporte para la API de Google Play Services Wearable Data Layer.
 *
 * Facilita la comunicación bidireccional mediante Bluetooth/WiFi entre el reloj inteligente
 * (WearOS) y el teléfono móvil vinculado (Mobile Hub), actuando este último como puente
 * de red hacia el servidor backend central.
 *
 * @property context Contexto de la aplicación Android.
 * @property onActiveOrderResponse Callback invocado al recibir los datos de la orden activa del repartidor.
 * @property onStatusUpdateResponse Callback invocado tras enviar una actualización de estado del pedido.
 * @property onHapticAlertReceived Callback invocado al recibir una alerta háptica del teléfono (ej. nuevo pedido o cancelación).
 */
class WearableDataLayerHelper(
    private val context: Context,
    private val onActiveOrderResponse: (success: Boolean, responseJson: JSONObject?) -> Unit,
    private val onStatusUpdateResponse: (success: Boolean, responseJson: JSONObject?) -> Unit,
    private val onHapticAlertReceived: (type: String) -> Unit
) : MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WearDataHelper"
        
        // Rutas de comunicación de datos de la API Data Layer
        private const val PATH_GET_ACTIVE = "/pedido/activo/get"
        private const val PATH_RESPONSE_ACTIVE = "/pedido/activo/response"
        private const val PATH_UPDATE_STATUS = "/pedido/status/update"
        private const val PATH_RESPONSE_STATUS = "/pedido/status/response"
        private const val PATH_ALERT = "/pedido/alerta"
    }

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    /**
     * Registra esta clase como receptor de mensajes entrantes de la Wearable Data Layer API.
     */
    fun registerListener() {
        messageClient.addListener(this)
        Log.d(TAG, "Message received listener registered")
    }

    /**
     * Remueve el registro de esta clase para dejar de recibir mensajes de la Wearable Data Layer API.
     */
    fun unregisterListener() {
        messageClient.removeListener(this)
        Log.d(TAG, "Message received listener unregistered")
    }

    /**
     * Recibe y procesa los mensajes entrantes de la Wearable Data Layer API enviados por el teléfono móvil.
     * Clasifica los payloads según la ruta asignada.
     *
     * @param event Evento de mensaje recibido que contiene la ruta y el payload en bytes.
     */
    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val payload = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "Received message on path: $path, payload: $payload")

        try {
            when (path) {
                PATH_RESPONSE_ACTIVE -> {
                    val json = JSONObject(payload)
                    val success = json.optBoolean("success", false)
                    val data = json.optJSONObject("data")
                    onActiveOrderResponse(success, data)
                }
                PATH_RESPONSE_STATUS -> {
                    val json = JSONObject(payload)
                    val success = json.optBoolean("success", false)
                    val data = json.optJSONObject("data")
                    onStatusUpdateResponse(success, data)
                }
                PATH_ALERT -> {
                    onHapticAlertReceived(payload)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming wearable message", e)
        }
    }

    /**
     * Envía una solicitud al teléfono móvil para obtener el pedido activo actual asignado al repartidor.
     *
     * @param repartidorId Identificador único del repartidor.
     */
    fun requestActiveOrder(repartidorId: Int) {
        Log.d(TAG, "Requesting active order for courier: $repartidorId")
        sendToConnectedNodes(PATH_GET_ACTIVE, repartidorId.toString())
    }

    /**
     * Envía una solicitud de actualización del estado de un pedido al teléfono móvil.
     *
     * @param orderId Identificador único del pedido.
     * @param estatus Código numérico del nuevo estatus solicitado.
     * @param repartidorId Identificador único del repartidor encargado.
     */
    fun requestStatusUpdate(orderId: Int, estatus: Int, repartidorId: Int) {
        Log.d(TAG, "Requesting status update for order: $orderId to: $estatus")
        val payload = JSONObject().apply {
            put("id_pedido", orderId)
            put("estatus", estatus)
            put("repartidorId", repartidorId)
        }.toString()

        sendToConnectedNodes(PATH_UPDATE_STATUS, payload)
    }

    /**
     * Transmite un mensaje en bytes a todos los dispositivos móviles (nodos) conectados al reloj.
     *
     * @param path Ruta semántica del mensaje.
     * @param payload Datos del mensaje formateados como String.
     */
    private fun sendToConnectedNodes(path: String, payload: String) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected phone nodes found to send message")
                    return@addOnSuccessListener
                }
                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, payload.toByteArray(Charsets.UTF_8))
                        .addOnSuccessListener {
                            Log.d(TAG, "Message successfully sent to node ${node.displayName} on path $path")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send message to node ${node.displayName} on path $path", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch connected nodes", e)
            }
    }
}
