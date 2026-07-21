package mx.utng.deliverytrack.mobile.data.models

data class Pedido(
    val id: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referenciaLugar: String,
    val descripcionPedido: String,
    val idRepartidor: Int,
    val estatus: Int
)
