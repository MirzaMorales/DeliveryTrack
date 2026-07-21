package mx.utng.deliverytrack.mobile.data.repository

import mx.utng.deliverytrack.mobile.data.models.Repartidor
import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource
import org.json.JSONArray
import org.json.JSONObject

class MobileRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {

    fun getRepartidores(callback: (Boolean, List<Repartidor>?, String?) -> Unit) {
        remoteDataSource.get("/api/usuarios/repartidores") { success, response ->
            if (success && response != null) {
                try {
                    val arr = JSONArray(response)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        Repartidor(
                            id = obj.getInt("id_user"),
                            nombre = obj.getString("nombre_completo"),
                            telefono = obj.getString("telefono")
                        )
                    }
                    callback(true, list, null)
                } catch (e: Exception) {
                    callback(false, null, e.message)
                }
            } else {
                callback(false, null, response ?: "Error de carga")
            }
        }
    }
}
