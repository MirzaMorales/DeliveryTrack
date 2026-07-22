package mx.utng.deliverytrack.mobile.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mx.utng.deliverytrack.mobile.ui.admin.AdminDashboardScreen
import mx.utng.deliverytrack.mobile.ui.admin.GestionUsuariosActivity
import mx.utng.deliverytrack.mobile.ui.auth.LoginScreen
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
import mx.utng.deliverytrack.mobile.ui.repartidor.DetallePedidoRepartidorActivity
import mx.utng.deliverytrack.mobile.ui.repartidor.MisPedidosRepartidorScreen

class MainActivity : ComponentActivity() {

    private var activeSession by mutableStateOf<UserSession?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this

        setContent {
            MaterialTheme {
                val session = activeSession
                if (session == null) {
                    LoginScreen(
                        onLoginSuccess = { user ->
                            activeSession = user
                        }
                    )
                } else if (session.rol == 1) { // Admin
                    AdminDashboardScreen(
                        userSession = session,
                        onCrearPedidoClick = {
                            context.startActivity(Intent(context, NuevoPedidoActivity::class.java))
                        },
                        onGestionUsuariosClick = {
                            context.startActivity(Intent(context, GestionUsuariosActivity::class.java))
                        },
                        onLogoutClick = {
                            activeSession = null
                        }
                    )
                } else { // Repartidor (rol == 2)
                    MisPedidosRepartidorScreen(
                        userSession = session,
                        onVerDetalleClick = { pedidoId ->
                            val intent = Intent(context, DetallePedidoRepartidorActivity::class.java).apply {
                                putExtra(DetallePedidoRepartidorActivity.EXTRA_ORDER_ID, pedidoId)
                                putExtra(DetallePedidoRepartidorActivity.EXTRA_COURIER_ID, session.idUser)
                            }
                            context.startActivity(intent)
                        },
                        onLogoutClick = {
                            activeSession = null
                        }
                    )
                }
            }
        }
    }
}
