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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.mobile.ui.NuevoPedidoActivity
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONObject
import kotlin.concurrent.thread

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

    LaunchedEffect(orderId) {
        fetchDetails()
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
                                .height(160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🗺️ UBICACIÓN INDIVIDUAL DE REPARTIDOR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Rastreo GPS de ${item.repartidorNombre} en mapa interactivo", color = Color(0xFF94A3B8), fontSize = 11.sp)
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
                                Text("✏️ Editar pedido", fontWeight = FontWeight.Bold, color = Color.White)
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
                                    Text("🚫 Cancelar pedido", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
