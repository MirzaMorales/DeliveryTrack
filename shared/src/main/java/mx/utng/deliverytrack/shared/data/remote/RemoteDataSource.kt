package mx.utng.deliverytrack.shared.data.remote

import mx.utng.deliverytrack.shared.config.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Origen de datos remoto para interactuar con la API REST del servidor mediante peticiones HTTP.
 * 
 * @property baseUrl URL base del servidor obtenida de la configuración.
 */
class RemoteDataSource(private val baseUrl: String = ServerConfig.BASE_URL) {
    private val client = OkHttpClient()

    /**
     * Realiza una petición GET asíncrona a un endpoint específico.
     * 
     * @param endpoint Ruta del recurso que se desea consultar.
     * @param callback Callback ejecutado al terminar la petición. Retorna un booleano (éxito/fallo) y el cuerpo de la respuesta o mensaje de error.
     */
    fun get(endpoint: String, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(false, e.message)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        callback(true, it.body?.string())
                    } else {
                        callback(false, "HTTP ${it.code}")
                    }
                }
            }
        })
    }
}
