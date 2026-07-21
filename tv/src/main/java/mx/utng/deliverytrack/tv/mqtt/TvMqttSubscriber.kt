package mx.utng.deliverytrack.tv.mqtt

import mx.utng.deliverytrack.shared.mqtt.MqttClientHelper

class TvMqttSubscriber(
    private val serverUri: String = "ssl://79a94522998842728c5ef7bf42fd3c30.s1.eu.hivemq.cloud:8883",
    private val username: String = "smarthealthmonitor",
    private val password: String = "linux123"
) {
    private var mqttClientHelper: MqttClientHelper? = null

    fun connectAndSubscribeAllCouriers(onTelemetryReceived: (String, String) -> Unit) {
        mqttClientHelper = MqttClientHelper(serverUri, "DeliveryTrack_TV_Dashboard", username, password)
        mqttClientHelper?.connect({
            mqttClientHelper?.subscribe("deliverytrack/telemetry/#", onTelemetryReceived)
        }) { _ -> }
    }

    fun disconnect() {
        mqttClientHelper?.disconnect()
    }
}
