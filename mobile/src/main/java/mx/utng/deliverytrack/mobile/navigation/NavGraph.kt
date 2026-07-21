package mx.utng.deliverytrack.mobile.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object MisPedidos : Screen("mis_pedidos")
    object DetallePedido : Screen("detalle_pedido/{pedidoId}") {
        fun createRoute(pedidoId: Int) = "detalle_pedido/$pedidoId"
    }
    object AdminDashboard : Screen("admin_dashboard")
    object NuevoPedido : Screen("nuevo_pedido")
    object GestionRepartidores : Screen("gestion_repartidores")
}
