package mx.utng.deliverytrack.mobile

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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

data class Repartidor(val id: Int, val nombre: String, val telefono: String)

class NuevoPedidoActivity : ComponentActivity() {

    private val backendUrl = "http://10.0.2.2:3000"
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NuevoPedidoScreen(
                    backendUrl = backendUrl,
                    onPedidoCreado = { finish() },
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
    onPedidoCreado: () -> Unit,
    onShowToast: (String) -> Unit
) {
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

    // Cargar repartidores al iniciar
    LaunchedEffect(Unit) {
        thread {
            try {
                val url = java.net.URL("$backendUrl/api/usuarios/repartidores")
                val conn = url.openConnection() as java.net.HttpURLConnection
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
                    // Actualizar estado desde hilo secundario (Compose MutableState es thread-safe)
                    repartidores = list
                    if (list.isNotEmpty()) repartidorSeleccionado = list[0]
                    isLoadingRepartidores = false
                } else {
                    errorRepartidores = "Error $code al cargar repartidores"
                    isLoadingRepartidores = false
                }
            } catch (e: Exception) {
                errorRepartidores = "Sin conexión: ${e.message}"
                isLoadingRepartidores = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(primaryBlue)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "← Nuevo Pedido",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // DATOS DEL CLIENTE
            Text(
                "DATOS DEL CLIENTE",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.DarkGray
            )

            PedidoTextField(
                value = nombreCliente,
                onValueChange = { nombreCliente = it },
                label = "Nombre del cliente",
                placeholder = "Ej. Carlos Méndez"
            )

            PedidoTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = "Teléfono",
                placeholder = "Ej. 555-123-4567",
                keyboardType = KeyboardType.Phone
            )

            PedidoTextField(
                value = direccion,
                onValueChange = { direccion = it },
                label = "Dirección de entrega",
                placeholder = "Calle, número, colonia"
            )

            PedidoTextField(
                value = referencia,
                onValueChange = { referencia = it },
                label = "Referencias del lugar",
                placeholder = "Ej. Casa azul, portón negro...",
                singleLine = false,
                minLines = 2
            )

            PedidoTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = "Descripción del pedido",
                placeholder = "Artículos, cantidad, notas...",
                singleLine = false,
                minLines = 3
            )

            Spacer(modifier = Modifier.height(4.dp))

            // REPARTIDOR
            Text(
                "REPARTIDOR SUGERIDO",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.DarkGray
            )

            when {
                isLoadingRepartidores -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = primaryBlue
                    )
                }
                errorRepartidores.isNotEmpty() -> {
                    Text(
                        text = errorRepartidores,
                        color = Color(0xFFD32F2F),
                        fontSize = 13.sp
                    )
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
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentBlue,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            repartidores.forEach { rep ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(rep.nombre, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                rep.telefono,
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    },
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

            Spacer(modifier = Modifier.height(8.dp))

            // BOTÓN CREAR
            Button(
                onClick = {
                    val rep = repartidorSeleccionado
                    if (nombreCliente.isBlank() || telefono.isBlank() || direccion.isBlank() || rep == null) {
                        onShowToast("Completa los campos obligatorios")
                        return@Button
                    }
                    isLoading = true
                    thread {
                        try {
                            val body = JSONObject().apply {
                                put("nombre_cliente", nombreCliente)
                                put("telefono", telefono)
                                put("direccion", direccion)
                                put("referencia_lugar", referencia)
                                put("descripcion_pedido", descripcion)
                                put("id_repartidor", rep.id)
                            }.toString()

                            val url = java.net.URL("$backendUrl/api/pedidos")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            conn.doOutput = true
                            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                            val code = conn.responseCode
                            isLoading = false
                            if (code == 201) {
                                onShowToast("¡Pedido creado exitosamente!")
                                onPedidoCreado()
                            } else {
                                val err = conn.errorStream?.bufferedReader()?.readText()
                                    ?: "Error desconocido"
                                onShowToast("Error $code: $err")
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            onShowToast("Error de red: ${e.message}")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading && !isLoadingRepartidores,
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Crear Pedido", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PedidoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFFADB5BD)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
}