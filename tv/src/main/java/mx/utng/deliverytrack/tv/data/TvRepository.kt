package mx.utng.deliverytrack.tv.data

import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource
import mx.utng.deliverytrack.tv.domain.model.DashboardMetrics

class TvRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {
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
