package mx.utng.deliverytrack.mobile.ui.admin

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

data class AdminPedidoItem(
    val idPedido: Int,
    val nombreCliente: String,
    val direccion: String,
    val repartidorNombre: String,
    val estatus: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    userSession: UserSession,
    onCrearPedidoClick: () -> Unit,
    onGestionUsuariosClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var pedidos by remember { mutableStateOf<List<AdminPedidoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF1A3A6B)

    fun fetchAdminPedidos() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/admin/activos")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        AdminPedidoItem(
                            idPedido = obj.getInt("id_pedido"),
                            nombreCliente = obj.getString("nombre_cliente"),
                            direccion = obj.getString("direccion"),
                            repartidorNombre = if (obj.isNull("repartidor_nombre")) "Sin asignar" else obj.getString("repartidor_nombre"),
                            estatus = obj.getInt("estatus")
                        )
                    }
                    pedidos = list
                } else {
                    errorMessage = "Error al cargar pedidos del sistema"
                }
            } catch (e: Exception) {
                errorMessage = "Error de conexión: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun cancelarPedido(orderId: Int) {
        thread {
            try {
                val body = JSONObject().apply {
                    put("estatus", 4) // Cancelado
                }.toString()

                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                if (conn.responseCode == 200) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, "Pedido #$orderId cancelado", Toast.LENGTH_SHORT).show()
                    }
                    fetchAdminPedidos()
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto-refresh when screen becomes active/resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchAdminPedidos()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("DeliveryTrack — Pedidos", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                },
                actions = {
                    Button(
                        onClick = onCrearPedidoClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onLogoutClick) {
                        Text("Salir", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    label = { Text("Pedidos") },
                    icon = { Icon(Icons.Default.List, contentDescription = "Pedidos") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onGestionUsuariosClick,
                    label = { Text("Usuarios") },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Usuarios") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header status banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mapa general de flotilla de repartidores activo", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Text(
                    text = "PEDIDOS ACTIVOS DEL SISTEMA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = primaryBlue)
                        }
                    }
                    errorMessage.isNotEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(errorMessage, color = Color(0xFFEF4444))
                        }
                    }
                    pedidos.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay pedidos registrados en el sistema", color = Color.Gray)
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pedidos) { item ->
                                AdminPedidoRow(
                                    item = item,
                                    onPedidoClick = { orderId ->
                                        val intent = Intent(context, AdminDetallePedidoActivity::class.java).apply {
                                            putExtra(AdminDetallePedidoActivity.EXTRA_ORDER_ID, orderId)
                                        }
                                        context.startActivity(intent)
                                    },
                                    onCancelarClick = { orderId ->
                                        cancelarPedido(orderId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPedidoRow(
    item: AdminPedidoItem,
    onPedidoClick: (Int) -> Unit,
    onCancelarClick: (Int) -> Unit
) {
    val (statusText, statusColor) = when (item.estatus) {
        1 -> "Aceptado" to Color(0xFF2563EB)
        2 -> "Pendiente" to Color(0xFFE65100)
        3 -> "En ruta" to Color(0xFF16A34A)
        4 -> "Cancelado" to Color(0xFFDC2626)
        5 -> "Retrasado" to Color(0xFFD97706)
        6 -> "Entregado" to Color(0xFF15803D)
        else -> "Estado ${item.estatus}" to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPedidoClick(item.idPedido) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("#${item.idPedido}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${item.repartidorNombre} • Cliente: ${item.nombreCliente}", fontSize = 13.sp, color = Color.DarkGray)
                Text(item.direccion, fontSize = 12.sp, color = Color.Gray)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (item.estatus != 4 && item.estatus != 6) {
                    IconButton(
                        onClick = { onCancelarClick(item.idPedido) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar pedido", tint = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}
