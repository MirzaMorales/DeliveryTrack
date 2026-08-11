package mx.utng.deliverytrack.shared.repository

import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource

/**
 * Repositorio de datos compartido entre módulos.
 * 
 * Centraliza la adquisición de datos de pedidos y recursos remotos comunes del sistema.
 * 
 * @property remoteDataSource Origen de datos remoto para realizar peticiones HTTP.
 */
class SharedRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {
    /**
     * Obtiene la lista de pedidos activos asignados a un repartidor en particular.
     * 
     * @param repartidorId Identificador único del repartidor.
     * @param callback Callback que retorna éxito/fallo y el JSON de respuesta como texto.
     */
    fun fetchActiveOrders(repartidorId: Int, callback: (Boolean, String?) -> Unit) {
        remoteDataSource.get("/api/pedidos/activo/repartidor/$repartidorId", callback)
    }
}
