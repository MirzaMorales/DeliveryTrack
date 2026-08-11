package mx.utng.deliverytrack.shared.mqtt

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Clase de utilidad para interactuar con un servidor de mensajería MQTT.
 * 
 * Abstrae la conexión, suscripción, publicación y desconexión utilizando la biblioteca Paho MQTT.
 * 
 * @property serverUri Dirección URI del servidor broker MQTT (por ejemplo, tcp://localhost:1883).
 * @property clientId Identificador único del cliente conectado.
 * @property username Nombre de usuario para la autenticación en el broker (opcional).
 * @property password Contraseña para la autenticación en el broker (opcional).
 */
class MqttClientHelper(
    private val serverUri: String,
    private val clientId: String,
    private val username: String? = null,
    private val password: String? = null
) {
    private var mqttClient: MqttClient? = null

    /**
     * Establece una conexión con el broker MQTT de forma asíncrona.
     * 
     * @param onConnected Callback ejecutado cuando la conexión se ha establecido correctamente.
     * @param onError Callback ejecutado si la conexión falla, retornando la excepción generada.
     */
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

    /**
     * Se suscribe a un tema específico del broker MQTT y define el callback para procesar los mensajes entrantes.
     * 
     * @param topic Nombre del tema/canal al que desea suscribirse.
     * @param onMessage Callback invocado cada vez que se recibe un mensaje, retornando el tema origen y el payload en texto.
     */
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

    /**
     * Publica un mensaje de texto en un tema específico del broker MQTT.
     * 
     * @param topic Tema al que se enviará el mensaje.
     * @param message Cuerpo del mensaje en formato de texto.
     * @param qos Nivel de calidad del servicio (QoS) para el envío (por defecto 1).
     */
    fun publish(topic: String, message: String, qos: Int = 1) {
        val mqttMessage = MqttMessage(message.toByteArray()).apply {
            this.qos = qos
        }
        mqttClient?.publish(topic, mqttMessage)
    }

    /**
     * Cierra la conexión activa con el broker MQTT de forma segura.
     */
    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (_: Exception) {}
    }
}
