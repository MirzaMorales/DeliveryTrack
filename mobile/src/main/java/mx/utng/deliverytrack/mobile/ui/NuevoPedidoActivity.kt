package mx.utng.deliverytrack.mobile.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.mobile.data.models.Repartidor
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

class NuevoPedidoActivity : ComponentActivity() {

    private val backendUrl = ServerConfig.BASE_URL
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val EXTRA_EDIT_ORDER_ID = "extra_edit_order_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editOrderId = intent.getIntExtra(EXTRA_EDIT_ORDER_ID, -1)

        setContent {
            MaterialTheme {
                NuevoPedidoScreen(
                    backendUrl = backendUrl,
                    editOrderId = if (editOrderId > 0) editOrderId else null,
                    onPedidoGuardado = { finish() },
                    onShowToast = { msg ->
                        mainHandler.post {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevoPedidoScreen(
    backendUrl: String,
    editOrderId: Int? = null,
    onPedidoGuardado: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val isEditMode = (editOrderId != null)

    var nombreCliente by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var referencia by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    var repartidores by remember { mutableStateOf<List<Repartidor>>(emptyList()) }
    var repartidorSeleccionado by remember { mutableStateOf<Repartidor?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isLoadingRepartidores by remember { mutableStateOf(true) }
    var errorRepartidores by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF1A3A6B)
    val accentBlue = Color(0xFF2563EB)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Fetch repartidores
    LaunchedEffect(Unit) {
        thread {
            try {
                val url = java.net.URL("$backendUrl/api/usuarios/repartidores")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val code = conn.responseCode
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(response)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        Repartidor(
                            id = obj.getInt("id_user"),
                            nombre = obj.getString("nombre_completo"),
                            telefono = obj.getString("telefono")
                        )
                    }
                    mainHandler.post {
                        repartidores = list
                        if (list.isNotEmpty() && repartidorSeleccionado == null) {
                            repartidorSeleccionado = list[0]
                        }
                        isLoadingRepartidores = false
                    }
                } else {
                    mainHandler.post {
                        errorRepartidores = "Error $code al cargar repartidores"
                        isLoadingRepartidores = false
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    errorRepartidores = "Error de red: ${e.message}"
                    isLoadingRepartidores = false
                }
            }
        }
    }

    // Fetch order details if in Edit mode
    LaunchedEffect(editOrderId) {
        if (editOrderId != null) {
            thread {
                try {
                    val url = java.net.URL("$backendUrl/api/pedidos/$editOrderId")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000

                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        val obj = JSONObject(text)
                        mainHandler.post {
                            nombreCliente = obj.optString("nombre_cliente", "")
                            telefono = obj.optString("telefono", "")
                            direccion = obj.optString("direccion", "")
                            referencia = obj.optString("referencia_lugar", "")
                            descripcion = obj.optString("descripcion_pedido", "")
                            val repId = obj.optInt("id_repartidor", -1)
                            if (repId > 0 && repartidores.isNotEmpty()) {
                                repartidores.find { it.id == repId }?.let {
                                    repartidorSeleccionado = it
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Editar Pedido #$editOrderId" else "Nuevo Pedido",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onPedidoGuardado) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditMode) "INFORMACIÓN DEL PEDIDO A EDITAR" else "DATOS DEL CLIENTE Y ENTREGA",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            // Nombre Cliente
            OutlinedTextField(
                value = nombreCliente,
                onValueChange = { nombreCliente = it },
                label = { Text("Nombre del cliente *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Teléfono
            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono de contacto *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Dirección
            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it },
                label = { Text("Dirección de entrega *") },
                modifier = Modifier.fillMaxWidth()
            )

            // Referencia
            OutlinedTextField(
                value = referencia,
                onValueChange = { referencia = it },
                label = { Text("Referencia del lugar") },
                modifier = Modifier.fillMaxWidth()
            )

            // Descripción
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción de los productos") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // Repartidor Selector
            Text(
                text = "REPARTIDOR ASIGNADO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            when {
                isLoadingRepartidores -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cargando repartidores disponibles...")
                    }
                }
                errorRepartidores.isNotEmpty() -> {
                    Text(errorRepartidores, color = Color.Red, fontSize = 13.sp)
                }
                repartidores.isEmpty() -> {
                    Text("No hay repartidores activos disponibles.", color = Color.Red, fontSize = 13.sp)
                }
                else -> {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = repartidorSeleccionado?.nombre ?: "Seleccionar repartidor",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            repartidores.forEach { rep ->
                                DropdownMenuItem(
                                    text = { Text("${rep.nombre} (${rep.telefono})") },
                                    onClick = {
                                        repartidorSeleccionado = rep
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save / Edit Submit Button
            Button(
                onClick = {
                    if (nombreCliente.isBlank() || telefono.isBlank() || direccion.isBlank() || repartidorSeleccionado == null) {
                        onShowToast("Completa los campos obligatorios y selecciona un repartidor")
                        return@Button
                    }

                    isLoading = true

                    thread {
                        try {
                            val body = JSONObject().apply {
                                put("nombre_cliente", nombreCliente.trim())
                                put("telefono", telefono.trim())
                                put("direccion", direccion.trim())
                                put("referencia_lugar", referencia.trim())
                                put("descripcion_pedido", descripcion.trim())
                                put("id_repartidor", repartidorSeleccionado?.id)
                            }.toString()

                            val urlString = if (isEditMode) "$backendUrl/api/pedidos/$editOrderId" else "$backendUrl/api/pedidos"
                            val url = java.net.URL(urlString)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            applySslBypass(conn)
                            conn.requestMethod = if (isEditMode) "PUT" else "POST"
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.connectTimeout = 6000
                            conn.readTimeout = 6000
                            conn.doOutput = true
                            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                            val code = conn.responseCode
                            isLoading = false

                            if (code == 200 || code == 201) {
                                onShowToast(if (isEditMode) "Pedido #$editOrderId actualizado exitosamente" else "Pedido creado exitosamente")
                                mainHandler.post { onPedidoGuardado() }
                            } else {
                                onShowToast("Error $code al guardar pedido")
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            onShowToast("Error de conexión: ${e.message}")
                        }
                    }
                },
                enabled = !isLoading && repartidorSeleccionado != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isEditMode) "Editar pedido" else "Guardar pedido",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
