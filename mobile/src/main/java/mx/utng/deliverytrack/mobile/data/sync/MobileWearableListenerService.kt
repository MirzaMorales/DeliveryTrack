package mx.utng.deliverytrack.mobile.data.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import mx.utng.deliverytrack.shared.config.ServerConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MobileWearableListenerService : WearableListenerService() {

    private val executor = Executors.newCachedThreadPool()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "MobileWearableService"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val path = messageEvent.path
        val payload = String(messageEvent.data, Charsets.UTF_8)
        val sourceNodeId = messageEvent.sourceNodeId

        Log.d(TAG, "Message received from Wearable. Path: $path, Payload: $payload")

        executor.execute {
            try {
                when (path) {
                    "/pedido/activo/get" -> fetchActiveOrder(payload, sourceNodeId)
                    "/pedido/status/update" -> updateOrderStatus(payload, sourceNodeId)
                    else -> Log.w(TAG, "Unknown message path: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message on path: $path", e)
            }
        }
    }

    private fun fetchActiveOrder(repartidorId: String, clientNodeId: String) {
        Log.d(TAG, "Fetching active order for courier ID: $repartidorId")
        val request = Request.Builder()
            .url("${ServerConfig.BASE_URL}/api/pedidos/activo?repartidorId=$repartidorId")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Backend response (Active Order): Code=${response.code}, Body=$responseBody")
                
                val resultObj = JSONObject().apply {
                    put("success", response.isSuccessful)
                    put("code", response.code)
                    put("data", if (responseBody.isNotEmpty()) JSONObject(responseBody) else null)
                }
                
                sendReply(clientNodeId, "/pedido/activo/response", resultObj.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling backend for active order", e)
            val errObj = JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
            sendReply(clientNodeId, "/pedido/activo/response", errObj.toString())
        }
    }

    private fun updateOrderStatus(jsonPayload: String, clientNodeId: String) {
        Log.d(TAG, "Updating order status with payload: $jsonPayload")
        try {
            val json = JSONObject(jsonPayload)
            val orderId = json.getInt("id_pedido")
            val estatus = json.getInt("estatus")
            val repartidorId = json.getInt("repartidorId")

            val requestBodyJson = JSONObject().apply {
                put("estatus", estatus)
                put("repartidorId", repartidorId)
            }

            val request = Request.Builder()
                .url("${ServerConfig.BASE_URL}/api/pedidos/$orderId/estatus")
                .patch(requestBodyJson.toString().toRequestBody(mediaTypeJson))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Backend response (Status Update): Code=${response.code}, Body=$responseBody")

                val resultObj = JSONObject().apply {
                    put("success", response.isSuccessful)
                    put("code", response.code)
                    put("data", if (responseBody.isNotEmpty()) JSONObject(responseBody) else null)
                }

                sendReply(clientNodeId, "/pedido/status/response", resultObj.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status or parsing payload", e)
            val errObj = JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
            sendReply(clientNodeId, "/pedido/status/response", errObj.toString())
        }
    }

    private fun sendReply(targetNodeId: String, path: String, response: String) {
        Log.d(TAG, "Sending reply to Wearable node $targetNodeId on path $path. Payload: $response")
        Wearable.getMessageClient(this)
            .sendMessage(targetNodeId, path, response.toByteArray(Charsets.UTF_8))
            .addOnSuccessListener {
                Log.d(TAG, "Reply sent successfully to path: $path")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send reply to path: $path", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
