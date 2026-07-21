package mx.utng.deliverytrack.shared.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class RemoteDataSource(private val baseUrl: String = "http://10.0.2.2:3000") {
    private val client = OkHttpClient()

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
