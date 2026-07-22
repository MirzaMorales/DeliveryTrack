package mx.utng.deliverytrack.wear.presentation.pedidos

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import kotlin.concurrent.thread

data class WearPedidoCardItem(
    val idPedido: Int,
    val nombreCliente: String,
    val direccion: String,
    val descripcion: String,
    val estatus: Int
)

@Composable
fun WearPedidosCardsScreen(
    courierId: Int,
    courierName: String,
    onPedidoCardClick: (WearPedidoCardItem) -> Unit,
    onChangeCourierClick: () -> Unit
) {
    var pedidos by remember { mutableStateOf<List<WearPedidoCardItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(courierId, reloadTrigger) {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/repartidor/$courierId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        WearPedidoCardItem(
                            idPedido = obj.getInt("id_pedido"),
                            nombreCliente = obj.getString("nombre_cliente"),
                            direccion = obj.getString("direccion"),
                            descripcion = obj.optString("descripcion_pedido", ""),
                            estatus = obj.getInt("estatus")
                        )
                    }
                    pedidos = list
                } else {
                    pedidos = emptyList()
                }
            } catch (_: Exception) {
                pedidos = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = courierName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Mis Entregas (${pedidos.size})",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Text(
                        text = "Buscando entregas...",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            } else if (pedidos.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sin entregas activas...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "esperando asignaciones...",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(pedidos.size) { index ->
                    val item = pedidos[index]
                    val statusText = when (item.estatus) {
                        1 -> "Aceptado"
                        2 -> "Pendiente"
                        3 -> "En camino"
                        4 -> "Cancelado"
                        5 -> "Retrasado"
                        6 -> "Entregado"
                        else -> "Estatus ${item.estatus}"
                    }

                    Card(
                        onClick = { onPedidoCardClick(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Pedido #${item.idPedido} • $statusText",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.nombreCliente,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                            Text(
                                text = "📍 ${item.direccion}",
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
            item {
                Button(
                    onClick = onChangeCourierClick,
                    modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("Cerrar sesión", fontSize = 9.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
