package mx.utng.deliverytrack.tv.domain.model

/**
 * Clase de datos que representa los indicadores clave de rendimiento (KPIs) del tablero de TV.
 * 
 * @property pedidosActivos Número de pedidos en estado activo (Aceptado, Pendiente, En camino, Retrasado).
 * @property entregadosHoy Cantidad de pedidos cuya entrega se concretó exitosamente el día de hoy.
 * @property tiempoPromedioMin Promedio del tiempo de entrega de los pedidos finalizados hoy en minutos.
 * @property incidencias Número de pedidos activos que registran algún tipo de retraso (estatus 5).
 * @property repartidoresEnRuta Cantidad de repartidores que están actualmente en camino entregando un pedido.
 */
data class DashboardMetrics(
    val pedidosActivos: Int = 0,
    val entregadosHoy: Int = 0,
    val tiempoPromedioMin: Int = 0,
    val incidencias: Int = 0,
    val repartidoresEnRuta: Int = 0
)
