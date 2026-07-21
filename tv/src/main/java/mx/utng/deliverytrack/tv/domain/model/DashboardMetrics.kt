package mx.utng.deliverytrack.tv.domain.model

data class DashboardMetrics(
    val pedidosActivos: Int = 0,
    val entregadosHoy: Int = 0,
    val tiempoPromedioMin: Int = 0,
    val incidencias: Int = 0,
    val repartidoresEnRuta: Int = 0
)
