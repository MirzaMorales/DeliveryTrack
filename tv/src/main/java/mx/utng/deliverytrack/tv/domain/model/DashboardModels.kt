package mx.utng.deliverytrack.tv.domain.model

/**
 * Representa los datos de un pedido transferidos desde el backend.
 * 
 * @property idPedido Identificador único del pedido.
 * @property nombreCliente Nombre del cliente destinatario del pedido.
 * @property telefono Teléfono de contacto del cliente.
 * @property direccion Dirección de entrega del pedido.
 * @property referenciaLugar Indicaciones o referencias del lugar de entrega.
 * @property descripcionPedido Detalle del contenido o notas del pedido.
 * @property estatus Código numérico del estado del pedido.
 * @property idRepartidor Identificador del repartidor asignado, o null si está sin asignar.
 */
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

/**
 * Representa la ubicación y datos de telemetría actuales de un repartidor.
 * 
 * @property repartidorId Identificador único del repartidor.
 * @property lat Coordenada de latitud actual.
 * @property lng Coordenada de longitud actual.
 * @property velocidad Velocidad instantánea en km/h.
 * @property bateria Porcentaje de carga de batería del dispositivo móvil.
 */
data class RepartidorUbicacionDto(
    val repartidorId: Int,
    val lat: Double,
    val lng: Double,
    val velocidad: Double,
    val bateria: Int
)

/**
 * Representa los indicadores clave de rendimiento (KPIs) generales recibidos en tiempo real.
 * 
 * @property pedidosActivos Cantidad de pedidos activos.
 * @property entregadosHoy Cantidad de pedidos entregados en la fecha actual.
 * @property tiempoPromedioMin Promedio del tiempo empleado para las entregas en minutos.
 * @property incidencias Cantidad de pedidos con retrasos reportados.
 * @property repartidoresEnRuta Cantidad de repartidores entregando actualmente en calle.
 */
data class KpisDto(
    val pedidosActivos: Int,
    val entregadosHoy: Int,
    val tiempoPromedioMin: Int,
    val incidencias: Int,
    val repartidoresEnRuta: Int
)

/**
 * Representa la jerarquía de eventos que son recibidos del backend mediante la conexión de WebSocket.
 */
sealed class WsEvent {
    /**
     * Estado inicial del sistema que contiene toda la información de pedidos, ubicaciones y métricas.
     */
    data class Snapshot(
        val pedidos: List<PedidoDto>,
        val repartidores: List<RepartidorUbicacionDto>,
        val kpis: KpisDto
    ) : WsEvent()

    /**
     * Evento emitido al crearse un nuevo pedido en el sistema.
     */
    data class PedidoCreado(val pedido: PedidoDto) : WsEvent()

    /**
     * Evento emitido al actualizarse el estado de un pedido.
     */
    data class PedidoActualizado(val pedidoId: Int, val nuevoEstatus: Int) : WsEvent()

    /**
     * Evento emitido cuando un repartidor reporta una actualización de su posición GPS y telemetría.
     */
    data class UbicacionActualizada(
        val repartidorId: Int,
        val lat: Double,
        val lng: Double,
        val velocidad: Double,
        val bateria: Int
    ) : WsEvent()
}
