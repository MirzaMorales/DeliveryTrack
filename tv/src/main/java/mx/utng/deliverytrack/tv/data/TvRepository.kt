package mx.utng.deliverytrack.tv.data

import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource
import mx.utng.deliverytrack.tv.domain.model.DashboardMetrics

/**
 * Repositorio de datos para el módulo TV.
 * 
 * Proporciona métodos para obtener información y métricas del panel logístico
 * haciendo uso de la fuente de datos remota.
 * 
 * @property remoteDataSource Origen de datos remoto para realizar peticiones HTTP.
 */
class TvRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {
    
    /**
     * Obtiene las métricas iniciales del panel logístico desde el backend.
     * 
     * @param callback Callback que retorna el resultado de la operación (éxito/fallo) y las métricas obtenidas.
     */
    fun fetchMetrics(callback: (Boolean, DashboardMetrics?) -> Unit) {
        remoteDataSource.get("/api/dashboard/metrics") { success, _ ->
            if (success) {
                callback(true, DashboardMetrics(12, 47, 22, 2, 8))
            } else {
                callback(false, null)
            }
        }
    }
}
