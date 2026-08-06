package mx.utng.deliverytrack.mobile.ui.admin

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
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

private fun applySslBypass(conn: java.net.HttpURLConnection) {
    if (conn is javax.net.ssl.HttpsURLConnection) {
        try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                }
            )
            val sc = javax.net.ssl.SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            conn.sslSocketFactory = sc.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }
}

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
    var showFleetMapModal by remember { mutableStateOf(false) }

    val primaryBlue = Color(0xFF1A3A6B)

    fun fetchAdminPedidos() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/admin/activos")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
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
                applySslBypass(conn)
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

    var selectedEstatusFilter by remember { mutableStateOf<Int?>(null) }
    var selectedRepartidorFilter by remember { mutableStateOf("Todos los repartidores") }
    var repartidorDropdownExpanded by remember { mutableStateOf(false) }

    val repartidoresDisponibles = remember(pedidos) {
        listOf("Todos los repartidores") + pedidos.map { it.repartidorNombre }.distinct().sorted()
    }

    val filteredPedidos = remember(pedidos, selectedEstatusFilter, selectedRepartidorFilter) {
        pedidos.filter { item ->
            val matchesStatus = (selectedEstatusFilter == null || item.estatus == selectedEstatusFilter)
            val matchesRepartidor = (selectedRepartidorFilter == "Todos los repartidores" ||
                                     item.repartidorNombre.equals(selectedRepartidorFilter, ignoreCase = true))
            matchesStatus && matchesRepartidor
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DeliveryTrack", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text("Gestión de Pedidos", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    }
                },
                actions = {
                    Button(
                        onClick = onCrearPedidoClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar sesión",
                            tint = Color.White
                        )
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
                // Header Fleet Map Card for Admin
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MONITOREO DE FLOTILLA Y ENTREGAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Surface(
                                color = Color(0xFF16A34A).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "EN TIEMPO REAL",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Supervisa la ubicación y ruta de los repartidores activos en el mapa.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showFleetMapModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver Mapa General de Flotilla (Google Maps)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Modal: Real Active Couriers Fleet Overview
                if (showFleetMapModal) {
                    AlertDialog(
                        onDismissRequest = { showFleetMapModal = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF2563EB))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Flotilla Activa de Repartidores", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        },
                        text = {
                            val activeOrdersWithCourier = pedidos.filter { it.repartidorNombre != "Sin asignar" }
                            if (activeOrdersWithCourier.isEmpty()) {
                                Text("No hay repartidores con entregas activas asignadas en este momento.", fontSize = 13.sp, color = Color.Gray)
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    activeOrdersWithCourier.forEach { item ->
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Repartidor: ${item.repartidorNombre}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = "Pedido #${item.idPedido} • Cliente: ${item.nombreCliente}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF475569)
                                                )
                                                Text(
                                                    text = "📍 ${item.direccion}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF2563EB),
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Button(
                                                    onClick = {
                                                        try {
                                                            val encodedAddress = java.net.URLEncoder.encode(item.direccion, "UTF-8")
                                                            val webMapIntent = android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedAddress")
                                                            )
                                                            context.startActivity(webMapIntent)
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "No se pudo abrir Google Maps", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("🗺️ Abrir Mapa de ${item.repartidorNombre}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showFleetMapModal = false }) {
                                Text("Cerrar", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                // Courier Dropdown Filter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = { repartidorDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Repartidor: $selectedRepartidorFilter",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Text("▼", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    DropdownMenu(
                        expanded = repartidorDropdownExpanded,
                        onDismissRequest = { repartidorDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        repartidoresDisponibles.forEach { repNombre ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = repNombre,
                                        fontWeight = if (repNombre == selectedRepartidorFilter) FontWeight.Bold else FontWeight.Normal,
                                        color = if (repNombre == selectedRepartidorFilter) primaryBlue else Color.Unspecified
                                    )
                                },
                                onClick = {
                                    selectedRepartidorFilter = repNombre
                                    repartidorDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Filter Bar by Order Status
                val filterOptions = listOf(
                    null to "Todos (${pedidos.size})",
                    2 to "Pendientes",
                    1 to "Aceptados",
                    3 to "En ruta",
                    5 to "Retrasados",
                    6 to "Entregados",
                    4 to "Cancelados"
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filterOptions) { (status, label) ->
                        val isSelected = selectedEstatusFilter == status
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedEstatusFilter = status },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF334155)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) primaryBlue else Color(0xFFCBD5E1),
                                selectedBorderColor = primaryBlue,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }

                Text(
                    text = "PEDIDOS EN EL SISTEMA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
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
                    filteredPedidos.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay pedidos para este filtro", color = Color.Gray)
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredPedidos) { item ->
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
