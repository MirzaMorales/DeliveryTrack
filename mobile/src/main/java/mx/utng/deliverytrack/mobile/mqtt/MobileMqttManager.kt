package mx.utng.deliverytrack.mobile.mqtt

import mx.utng.deliverytrack.shared.mqtt.MqttClientHelper

class MobileMqttManager(
    private val serverUri: String = "ssl://79a94522998842728c5ef7bf42fd3c30.s1.eu.hivemq.cloud:8883",
    private val username: String = "smarthealthmonitor",
    private val password: String = "linux123"
) {
    private var mqttClientHelper: MqttClientHelper? = null

    fun connect(clientId: String, onConnected: () -> Unit) {
        mqttClientHelper = MqttClientHelper(serverUri, clientId, username, password)
        mqttClientHelper?.connect(onConnected) { _ -> }
    }

    fun publishTelemetry(courierId: Int, lat: Double, lng: Double, speed: Float) {
        val payload = """{"id_repartidor":$courierId,"lat":$lat,"lng":$lng,"speed":$speed}"""
        mqttClientHelper?.publish("deliverytrack/telemetry/$courierId", payload)
    }

    fun disconnect() {
        mqttClientHelper?.disconnect()
    }
}
