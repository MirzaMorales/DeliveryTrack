package mx.utng.deliverytrack.shared.repository

import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource

class SharedRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {
    fun fetchActiveOrders(repartidorId: Int, callback: (Boolean, String?) -> Unit) {
        remoteDataSource.get("/api/pedidos/activo/repartidor/$repartidorId", callback)
    }
}
