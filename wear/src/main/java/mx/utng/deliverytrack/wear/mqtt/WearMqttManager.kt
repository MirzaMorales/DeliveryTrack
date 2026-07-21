package mx.utng.deliverytrack.wear.mqtt

import mx.utng.deliverytrack.shared.mqtt.MqttClientHelper

class WearMqttManager(
    private val serverUri: String = "ssl://79a94522998842728c5ef7bf42fd3c30.s1.eu.hivemq.cloud:8883",
    private val username: String = "smarthealthmonitor",
    private val password: String = "linux123"
) {
    private var mqttClientHelper: MqttClientHelper? = null

    fun connect(clientId: String, onConnected: () -> Unit) {
        mqttClientHelper = MqttClientHelper(serverUri, clientId, username, password)
        mqttClientHelper?.connect(onConnected) { _ -> }
    }

    fun subscribeToCourierTopic(courierId: Int, onAlertReceived: (String) -> Unit) {
        mqttClientHelper?.subscribe("deliverytrack/courier/$courierId/alerts") { _, message ->
            onAlertReceived(message)
        }
    }

    fun disconnect() {
        mqttClientHelper?.disconnect()
    }
}
