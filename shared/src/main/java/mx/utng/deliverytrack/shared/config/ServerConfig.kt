package mx.utng.deliverytrack.shared.config

/**
 * Objeto de configuración global para almacenar los endpoints de conexión con el servidor.
 */
object ServerConfig {
    /**
     * URL base de la API REST del servidor.
     * Puede apuntar a un servidor de desarrollo local o producción en la nube.
     */
    var BASE_URL: String = "https://deliverytrack-d9ut.onrender.com"

    /**
     * URL base del WebSocket del servidor para transferencia de datos en tiempo real.
     */
    var WS_URL: String = "wss://deliverytrack-d9ut.onrender.com/ws"
}
