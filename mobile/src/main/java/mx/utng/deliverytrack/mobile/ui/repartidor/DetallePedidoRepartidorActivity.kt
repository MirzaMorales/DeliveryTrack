package mx.utng.deliverytrack.mobile.ui.repartidor

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mx.utng.deliverytrack.shared.config.ServerConfig
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

data class RepartidorOrderDetail(
    val idPedido: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referencia: String,
    val descripcion: String,
    val estatus: Int,
    val repartidorId: Int
)

class DetallePedidoRepartidorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_COURIER_ID = "extra_courier_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getIntExtra(EXTRA_ORDER_ID, -1)
        val courierId = intent.getIntExtra(EXTRA_COURIER_ID, -1)

        setContent {
            MaterialTheme {
                DetallePedidoRepartidorScreen(
                    orderId = orderId,
                    courierId = courierId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetallePedidoRepartidorScreen(
    orderId: Int,
    courierId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var orderDetail by remember { mutableStateOf<RepartidorOrderDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUpdatingStatus by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF1A3A6B)

    fun fetchDetails() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val obj = JSONObject(text)
                    orderDetail = RepartidorOrderDetail(
                        idPedido = obj.getInt("id_pedido"),
                        nombreCliente = obj.getString("nombre_cliente"),
                        telefono = obj.getString("telefono"),
                        direccion = obj.getString("direccion"),
                        referencia = obj.optString("referencia_lugar", ""),
                        descripcion = obj.optString("descripcion_pedido", ""),
                        estatus = obj.getInt("estatus"),
                        repartidorId = obj.optInt("id_repartidor", courierId)
                    )
                } else {
                    errorMessage = "Error al cargar pedido"
                }
            } catch (e: Exception) {
                errorMessage = "Error de conexión: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun updateStatus(newStatus: Int) {
        isUpdatingStatus = true
        thread {
            try {
                val body = JSONObject().apply {
                    put("estatus", newStatus)
                    put("repartidorId", if (courierId > 0) courierId else 2)
                }.toString()

                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId/estatus")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                if (conn.responseCode == 200) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        val msg = when (newStatus) {
                            1 -> "Pedido aceptado"
                            3 -> "En camino a entregar"
                            4 -> "Pedido rechazado"
                            6 -> "¡Entrega completada exitosamente!"
                            else -> "Estatus actualizado"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    fetchDetails()
                } else {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, "Error al actualizar estado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isUpdatingStatus = false
            }
        }
    }

    // Auto-refresh order details on screen resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchDetails()
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
                    Text("Detalle de Entrega #$orderId", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryBlue)
                    }
                }
                errorMessage.isNotEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage, color = Color(0xFFEF4444))
                    }
                }
                orderDetail != null -> {
                    val item = orderDetail!!
                    val (statusText, statusColor) = when (item.estatus) {
                        1 -> "Aceptado" to Color(0xFF2563EB)
                        2 -> "Pendiente" to Color(0xFFE65100)
                        3 -> "En camino" to Color(0xFF16A34A)
                        4 -> "Cancelado" to Color(0xFFDC2626)
                        5 -> "Retrasado" to Color(0xFFD97706)
                        6 -> "Entregado" to Color(0xFF15803D)
                        else -> "Estado ${item.estatus}" to Color.Gray
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Status Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pedido #${item.idPedido}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // Wireframe 1.4: GPS Route & Fast Delivery Map (Active when Accepted / En camino)
                        if (item.estatus == 1 || item.estatus == 3 || item.estatus == 5) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("RUTA RÁPIDA DE NAVEGACIÓN GPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Origen: Tu ubicación actual ➔ Destino: ${item.direccion}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            try {
                                                val encodedAddress = java.net.URLEncoder.encode(item.direccion, "UTF-8")
                                                val gmmIntentUri = android.net.Uri.parse("google.navigation:q=$encodedAddress")
                                                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri).apply {
                                                    setPackage("com.google.android.apps.maps")
                                                }
                                                if (mapIntent.resolveActivity(context.packageManager) != null) {
                                                    context.startActivity(mapIntent)
                                                } else {
                                                    val webMapIntent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedAddress")
                                                    )
                                                    context.startActivity(webMapIntent)
                                                }
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "No se pudo abrir Google Maps", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Iniciar Navegación GPS (Google Maps)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        // Order Client & Delivery Info
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("INFORMACIÓN DE ENTREGA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)

                                Text("Cliente: ${item.nombreCliente}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("Teléfono: ${item.telefono}", fontSize = 14.sp, color = Color.DarkGray)
                                Text("Dirección: ${item.direccion}", fontSize = 14.sp, color = Color.DarkGray)
                                if (item.referencia.isNotEmpty()) {
                                    Text("Referencia: ${item.referencia}", fontSize = 13.sp, color = Color.Gray)
                                }
                                if (item.descripcion.isNotEmpty()) {
                                    Text("Descripción: ${item.descripcion}", fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Status Action Buttons for Courier
                        when (item.estatus) {
                            2 -> { // Pendiente
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { updateStatus(4) }, // Rechazar / Cancelar
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        enabled = !isUpdatingStatus,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Rechazar", fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Button(
                                        onClick = { updateStatus(1) }, // Aceptar
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        enabled = !isUpdatingStatus,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Aceptar Pedido", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            1 -> { // Aceptado -> En camino
                                Button(
                                    onClick = { updateStatus(3) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = !isUpdatingStatus,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("En camino", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                }
                            }
                            3, 5 -> { // En camino / Retrasado -> Entregado
                                Button(
                                    onClick = { updateStatus(6) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = !isUpdatingStatus,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Marcar como Entregado", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                }
                            }
                            6 -> { // Entregado
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "¡Entrega completada exitosamente!",
                                            color = Color(0xFF15803D),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
