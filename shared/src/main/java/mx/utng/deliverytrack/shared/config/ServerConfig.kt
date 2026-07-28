package mx.utng.deliverytrack.shared.config

object ServerConfig {
    /**
     * Configuración central de la URL del Backend:
     * - Para Emulador de Android Studio: "http://10.0.2.2:3000"
     * - Para Dispositivo Físico (misma red Wi-Fi): "http://<TU_IP_LOCAL>:3000" (ejemplo: "http://192.168.1.75:3000")
     * - Para Producción en Servidor Remoto: "https://tu-api.onrender.com"
     */
    var BASE_URL: String = "http://10.0.2.2:3000"
    var WS_URL: String = "ws://10.0.2.2:3000/ws"
}
