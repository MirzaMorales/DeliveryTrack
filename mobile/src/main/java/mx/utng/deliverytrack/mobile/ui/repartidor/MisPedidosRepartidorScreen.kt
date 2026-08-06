package mx.utng.deliverytrack.mobile.ui.repartidor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mx.utng.deliverytrack.mobile.R
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTab by remember { mutableIntStateOf(0) } // 0: Mis Entregas, 1: Perfil

    var pedidos by remember { mutableStateOf<List<PedidoCard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedEstatusFilter by remember { mutableStateOf<Int?>(null) }

    // State for Profile Tab
    var profileNombre by remember { mutableStateOf(userSession.nombreCompleto) }
    var profileTelefono by remember { mutableStateOf(userSession.telefono) }
    var profileContrasena by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }

    val primaryBlue = Color(0xFF1A3A6B)
    val accentBlue = Color(0xFF2563EB)

    fun fetchRepartidorPedidos() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/repartidor/${userSession.idUser}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
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

    // Auto-refresh when screen becomes active/resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchRepartidorPedidos()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredPedidos = remember(pedidos, selectedEstatusFilter) {
        if (selectedEstatusFilter == null) {
            pedidos
        } else {
            pedidos.filter { it.estatus == selectedEstatusFilter }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (currentTab == 0) "Mis Entregas" else "Mi Perfil",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Repartidor: ${profileNombre.ifBlank { userSession.nombreCompleto }}",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                },
                actions = {
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
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Mis Entregas"
                        )
                    },
                    label = { Text("Mis Entregas", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentBlue,
                        selectedTextColor = accentBlue,
                        indicatorColor = accentBlue.copy(alpha = 0.15f),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )

                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil"
                        )
                    },
                    label = { Text("Perfil", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentBlue,
                        selectedTextColor = accentBlue,
                        indicatorColor = accentBlue.copy(alpha = 0.15f),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF1F5F9))
        ) {
            if (currentTab == 0) {
                // TAB 0: MIS ENTREGAS
                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter bar for Repartidor
                    val filterOptions = listOf(
                        null to "Todos (${pedidos.size})",
                        2 to "Pendientes",
                        1 to "Aceptados",
                        3 to "En camino",
                        6 to "Entregados",
                        4 to "Cancelados"
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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

                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = primaryBlue)
                            }
                        }
                        errorMessage.isNotEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = errorMessage, color = Color(0xFFD32F2F))
                            }
                        }
                        filteredPedidos.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Sin pedidos para el filtro seleccionado", fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                        }
                        else -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredPedidos) { item ->
                                    PedidoItemCard(item = item, onClick = { onVerDetalleClick(item.idPedido) })
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 1: PERFIL DE REPARTIDOR
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Card with Avatar Greeting
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = accentBlue.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, accentBlue)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Avatar",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = "¡Hola, ${profileNombre.ifBlank { userSession.nombreCompleto }}!",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Repartidor en Flotilla Activa",
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Form Card (Editar Datos de Cuenta)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "DATOS DE LA CUENTA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                letterSpacing = 0.5.sp
                            )

                            // Nombre Completo Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Nombre Completo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                OutlinedTextField(
                                    value = profileNombre,
                                    onValueChange = { profileNombre = it },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = accentBlue) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Teléfono Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Teléfono de Contacto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                OutlinedTextField(
                                    value = profileTelefono,
                                    onValueChange = { profileTelefono = it },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = accentBlue) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Nueva Contraseña Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Cambiar Contraseña (Opcional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                OutlinedTextField(
                                    value = profileContrasena,
                                    onValueChange = { profileContrasena = it },
                                    placeholder = { Text("Escribe para cambiar la contraseña", fontSize = 12.sp, color = Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = accentBlue) },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                painter = painterResource(id = if (passwordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                                                contentDescription = "Ver contraseña",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Read-only User Role Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = accentBlue.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "ROL DE ACCESO",
                                    color = accentBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Repartidor de Operaciones (Rol 2)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                Text("ID de Usuario: #${userSession.idUser} • Administrado por Central", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    // Save Profile Button
                    Button(
                        onClick = {
                            if (profileNombre.isBlank() || profileTelefono.isBlank()) {
                                Toast.makeText(context, "El nombre y teléfono son obligatorios", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isSavingProfile = true
                            thread {
                                try {
                                    val bodyObj = JSONObject().apply {
                                        put("nombre_completo", profileNombre.trim())
                                        put("telefono", profileTelefono.trim())
                                        if (profileContrasena.isNotBlank()) {
                                            put("contrasena", profileContrasena)
                                        }
                                        put("rol", 2)
                                        put("estatus", 1)
                                    }

                                    val url = java.net.URL("${ServerConfig.BASE_URL}/api/usuarios/${userSession.idUser}")
                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                    applySslBypass(conn)
                                    conn.requestMethod = "PUT"
                                    conn.setRequestProperty("Content-Type", "application/json")
                                    conn.connectTimeout = 6000
                                    conn.readTimeout = 6000
                                    conn.doOutput = true
                                    conn.outputStream.write(bodyObj.toString().toByteArray(Charsets.UTF_8))

                                    val code = conn.responseCode
                                    isSavingProfile = false

                                    (context as? android.app.Activity)?.runOnUiThread {
                                        if (code == 200) {
                                            profileContrasena = ""
                                            Toast.makeText(context, "¡Perfil actualizado exitosamente!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Error $code al actualizar perfil", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    isSavingProfile = false
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSavingProfile,
                        colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isSavingProfile) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Guardar Cambios", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
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
