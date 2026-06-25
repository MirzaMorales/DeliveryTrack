package mx.utng.deliverytrack.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class WearableDataLayerHelper(
    private val context: Context,
    private val onActiveOrderResponse: (success: Boolean, responseJson: JSONObject?) -> Unit,
    private val onStatusUpdateResponse: (success: Boolean, responseJson: JSONObject?) -> Unit,
    private val onHapticAlertReceived: (type: String) -> Unit
) : MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WearDataHelper"
        private const val PATH_GET_ACTIVE = "/pedido/activo/get"
        private const val PATH_RESPONSE_ACTIVE = "/pedido/activo/response"
        private const val PATH_UPDATE_STATUS = "/pedido/status/update"
        private const val PATH_RESPONSE_STATUS = "/pedido/status/response"
        private const val PATH_ALERT = "/pedido/alerta"
    }

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    fun registerListener() {
        messageClient.addListener(this)
        Log.d(TAG, "Message received listener registered")
    }

    fun unregisterListener() {
        messageClient.removeListener(this)
        Log.d(TAG, "Message received listener unregistered")
    }

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

    fun requestActiveOrder(repartidorId: Int) {
        Log.d(TAG, "Requesting active order for courier: $repartidorId")
        sendToConnectedNodes(PATH_GET_ACTIVE, repartidorId.toString())
    }

    fun requestStatusUpdate(orderId: Int, estatus: Int, repartidorId: Int) {
        Log.d(TAG, "Requesting status update for order: $orderId to: $estatus")
        val payload = JSONObject().apply {
            put("id_pedido", orderId)
            put("estatus", estatus)
            put("repartidorId", repartidorId)
        }.toString()

        sendToConnectedNodes(PATH_UPDATE_STATUS, payload)
    }

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
