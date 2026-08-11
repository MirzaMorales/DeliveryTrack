package mx.utng.deliverytrack.wear.mqtt

import mx.utng.deliverytrack.shared.mqtt.MqttClientHelper

/**
 * Gestor de conexión MQTT para el módulo WearOS.
 *
 * Esta clase se encarga de abrir una conexión persistente a un broker MQTT
 * y suscribir al repartidor a su canal específico de notificaciones para recibir
 * alertas hápticas y actualizaciones críticas desde la central lograda mediante HiveMQ.
 *
 * @property serverUri Dirección del servidor broker MQTT (soporta conexiones seguras ssl://).
 * @property username Nombre de usuario para la autenticación en el broker.
 * @property password Contraseña para la autenticación en el broker.
 */
class WearMqttManager(
    private val serverUri: String = "ssl://79a94522998842728c5ef7bf42fd3c30.s1.eu.hivemq.cloud:8883",
    private val username: String = "smarthealthmonitor",
    private val password: String = "linux123"
) {
    private var mqttClientHelper: MqttClientHelper? = null

    /**
     * Establece la conexión con el broker MQTT utilizando un ID de cliente único.
     *
     * @param clientId Identificador único para el cliente MQTT en esta sesión.
     * @param onConnected Función callback que se ejecuta al establecerse la conexión con éxito.
     */
    fun connect(clientId: String, onConnected: () -> Unit) {
        mqttClientHelper = MqttClientHelper(serverUri, clientId, username, password)
        mqttClientHelper?.connect(onConnected) { _ -> }
    }

    /**
     * Suscribe al cliente al canal específico de alertas del repartidor actual.
     *
     * @param courierId Identificador único del repartidor.
     * @param onAlertReceived Función callback que se ejecuta cuando se recibe una alerta en formato JSON o texto.
     */
    fun subscribeToCourierTopic(courierId: Int, onAlertReceived: (String) -> Unit) {
        mqttClientHelper?.subscribe("deliverytrack/courier/$courierId/alerts") { _, message ->
            onAlertReceived(message)
        }
    }

    /**
     * Desconecta el cliente MQTT activo y libera los recursos.
     */
    fun disconnect() {
        mqttClientHelper?.disconnect()
    }
}
