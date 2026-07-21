package mx.utng.deliverytrack.shared.mqtt

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttClientHelper(
    private val serverUri: String,
    private val clientId: String,
    private val username: String? = null,
    private val password: String? = null
) {
    private var mqttClient: MqttClient? = null

    fun connect(onConnected: () -> Unit, onError: (Throwable) -> Unit) {
        try {
            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())
            val user = username
            val pass = password
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                if (!user.isNullOrEmpty()) {
                    userName = user
                }
                if (!pass.isNullOrEmpty()) {
                    this.password = pass.toCharArray()
                }
            }
            mqttClient?.connect(options)
            onConnected()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun subscribe(topic: String, onMessage: (String, String) -> Unit) {
        mqttClient?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {}

            override fun messageArrived(topicReceived: String?, message: MqttMessage?) {
                if (topicReceived != null && message != null) {
                    onMessage(topicReceived, String(message.payload))
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
        mqttClient?.subscribe(topic)
    }

    fun publish(topic: String, message: String, qos: Int = 1) {
        val mqttMessage = MqttMessage(message.toByteArray()).apply {
            this.qos = qos
        }
        mqttClient?.publish(topic, mqttMessage)
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (_: Exception) {}
    }
}
