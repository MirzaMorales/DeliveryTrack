package mx.utng.deliverytrack.tv.domain.model

data class PedidoDto(
    val idPedido: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referenciaLugar: String,
    val descripcionPedido: String,
    val estatus: Int,
    val idRepartidor: Int?
)

data class RepartidorUbicacionDto(
    val repartidorId: Int,
    val lat: Double,
    val lng: Double,
    val velocidad: Double,
    val bateria: Int
)

data class KpisDto(
    val pedidosActivos: Int,
    val entregadosHoy: Int,
    val tiempoPromedioMin: Int,
    val incidencias: Int,
    val repartidoresEnRuta: Int
)

sealed class WsEvent {
    data class Snapshot(
        val pedidos: List<PedidoDto>,
        val repartidores: List<RepartidorUbicacionDto>,
        val kpis: KpisDto
    ) : WsEvent()

    data class PedidoCreado(val pedido: PedidoDto) : WsEvent()

    data class PedidoActualizado(val pedidoId: Int, val nuevoEstatus: Int) : WsEvent()

    data class UbicacionActualizada(
        val repartidorId: Int,
        val lat: Double,
        val lng: Double,
        val velocidad: Double,
        val bateria: Int
    ) : WsEvent()
}
