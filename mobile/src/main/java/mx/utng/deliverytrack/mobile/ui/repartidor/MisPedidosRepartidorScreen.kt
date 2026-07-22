package mx.utng.deliverytrack.mobile.ui.repartidor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import kotlin.concurrent.thread

data class PedidoCard(
    val idPedido: Int,
    val nombreCliente: String,
    val direccion: String,
    val estatus: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisPedidosRepartidorScreen(
    userSession: UserSession,
    onVerDetalleClick: (Int) -> Unit,
    onLogoutClick: () -> Unit
) {
    var pedidos by remember { mutableStateOf<List<PedidoCard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF1A3A6B)

    LaunchedEffect(Unit) {
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/repartidor/${userSession.idUser}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        PedidoCard(
                            idPedido = obj.getInt("id_pedido"),
                            nombreCliente = obj.getString("nombre_cliente"),
                            direccion = obj.getString("direccion"),
                            estatus = obj.getInt("estatus")
                        )
                    }
                    pedidos = list
                } else {
                    errorMessage = "Error al cargar entregas"
                }
            } catch (e: Exception) {
                errorMessage = "Error de red: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis Entregas", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        Text(
                            "Hola, ${userSession.nombreCompleto} • ${pedidos.size} pedidos asignados",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogoutClick) {
                        Text("Salir", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF1F5F9))
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = primaryBlue
                    )
                }
                errorMessage.isNotEmpty() -> {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                pedidos.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Sin pedidos asignados por el momento", fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pedidos) { item ->
                            PedidoItemCard(item = item, onClick = { onVerDetalleClick(item.idPedido) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PedidoItemCard(item: PedidoCard, onClick: () -> Unit) {
    val (statusLabel, statusColor) = when (item.estatus) {
        1 -> "Aceptado" to Color(0xFF1976D2)
        2 -> "Pendiente" to Color(0xFFE65100)
        3 -> "En camino" to Color(0xFF388E3C)
        4 -> "Cancelado" to Color(0xFFD32F2F)
        5 -> "Retrasado" to Color(0xFFF57C00)
        6 -> "Entregado" to Color(0xFF43A047)
        else -> "Estado ${item.estatus}" to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pedido #${item.idPedido}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1E293B)
                )

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.nombreCliente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = item.direccion, fontSize = 13.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Ver detalle ›",
                color = Color(0xFF2563EB),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
