package mx.utng.deliverytrack.mobile.ui.admin

import android.content.Intent
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import mx.utng.deliverytrack.mobile.ui.NuevoPedidoActivity
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

data class AdminOrderDetail(
    val idPedido: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referencia: String,
    val descripcion: String,
    val estatus: Int,
    val repartidorNombre: String,
    val repartidorTelefono: String
)

class AdminDetallePedidoActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getIntExtra(EXTRA_ORDER_ID, -1)

        setContent {
            MaterialTheme {
                AdminDetallePedidoScreen(
                    orderId = orderId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDetallePedidoScreen(
    orderId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var orderDetail by remember { mutableStateOf<AdminOrderDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var isCanceling by remember { mutableStateOf(false) }

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
                    orderDetail = AdminOrderDetail(
                        idPedido = obj.getInt("id_pedido"),
                        nombreCliente = obj.getString("nombre_cliente"),
                        telefono = obj.getString("telefono"),
                        direccion = obj.getString("direccion"),
                        referencia = obj.optString("referencia_lugar", ""),
                        descripcion = obj.optString("descripcion_pedido", ""),
                        estatus = obj.getInt("estatus"),
                        repartidorNombre = if (obj.isNull("repartidor_nombre")) "Sin asignar" else obj.getString("repartidor_nombre"),
                        repartidorTelefono = obj.optString("repartidor_telefono", "")
                    )
                } else {
                    errorMessage = "Error al cargar detalle del pedido"
                }
            } catch (e: Exception) {
                errorMessage = "Error de red: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Auto-refresh order details whenever the screen resumes (e.g., returning from editing)
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
                    Text("Detalle del Pedido #$orderId", fontWeight = FontWeight.Bold, color = Color.White)
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
                .background(Color(0xFFF8FAFC))
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
                        3 -> "En ruta" to Color(0xFF16A34A)
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
                        // Header Status & Id Card
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
                                Column {
                                    Text("Pedido #${item.idPedido}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Repartidor: ${item.repartidorNombre}", fontSize = 13.sp, color = Color.DarkGray)
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Individual Courier GPS Map (Matching Wireframe Image 5)
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
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RASTREO GPS EN MAPA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Repartidor: ${item.repartidorNombre} ➔ Destino: ${item.direccion}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ver Ruta GPS en Google Maps", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        // Order Info Details
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("DATOS DE LA ENTREGA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)

                                Text("Cliente: ${item.nombreCliente}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Teléfono: ${item.telefono}", fontSize = 13.sp, color = Color.DarkGray)
                                Text("Dirección: ${item.direccion}", fontSize = 13.sp, color = Color.DarkGray)
                                if (item.referencia.isNotEmpty()) {
                                    Text("Referencia: ${item.referencia}", fontSize = 13.sp, color = Color.Gray)
                                }
                                if (item.descripcion.isNotEmpty()) {
                                    Text("Descripción: ${item.descripcion}", fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons: Editar Pedido & Cancelar Pedido
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, NuevoPedidoActivity::class.java).apply {
                                        putExtra(NuevoPedidoActivity.EXTRA_EDIT_ORDER_ID, item.idPedido)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editar pedido", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            if (item.estatus != 4 && item.estatus != 6) {
                                Button(
                                    onClick = {
                                        isCanceling = true
                                        thread {
                                            try {
                                                val body = JSONObject().apply {
                                                    put("estatus", 4) // Cancelado
                                                }.toString()

                                                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/${item.idPedido}")
                                                val conn = url.openConnection() as java.net.HttpURLConnection
                                                conn.requestMethod = "PUT"
                                                conn.setRequestProperty("Content-Type", "application/json")
                                                conn.connectTimeout = 5000
                                                conn.doOutput = true
                                                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                                                if (conn.responseCode == 200) {
                                                    (context as? android.app.Activity)?.runOnUiThread {
                                                        Toast.makeText(context, "Pedido #${item.idPedido} cancelado", Toast.LENGTH_SHORT).show()
                                                    }
                                                    fetchDetails()
                                                }
                                            } catch (e: Exception) {
                                                (context as? android.app.Activity)?.runOnUiThread {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            } finally {
                                                isCanceling = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    enabled = !isCanceling,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancelar pedido", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
