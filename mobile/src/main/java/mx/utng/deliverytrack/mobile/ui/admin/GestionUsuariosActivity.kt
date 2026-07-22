package mx.utng.deliverytrack.mobile.ui.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

data class UserItem(
    val idUser: Int,
    val nombreCompleto: String,
    val telefono: String,
    val rol: Int,
    val estatus: Int
)

class GestionUsuariosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GestionUsuariosScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionUsuariosScreen(onBackClick: () -> Unit) {
    var usuarios by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var showDialogNuevo by remember { mutableStateOf(false) }

    val primaryBlue = Color(0xFF1A3A6B)

    fun fetchUsuarios() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/usuarios")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        UserItem(
                            idUser = obj.getInt("id_user"),
                            nombreCompleto = obj.getString("nombre_completo"),
                            telefono = obj.getString("telefono"),
                            rol = obj.getInt("rol"),
                            estatus = obj.getInt("estatus")
                        )
                    }
                    usuarios = list
                } else {
                    errorMessage = "Error al obtener usuarios"
                }
            } catch (e: Exception) {
                errorMessage = "Error de red: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchUsuarios()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Gestión de Usuarios", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Button(
                        onClick = { showDialogNuevo = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ Nuevo", color = Color.White, fontWeight = FontWeight.Bold)
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
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "USUARIOS REGISTRADOS EN EL SISTEMA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
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
                    usuarios.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay usuarios registrados", color = Color.Gray)
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(usuarios) { user ->
                                UserRowCard(user = user, onUserUpdated = { fetchUsuarios() })
                            }
                        }
                    }
                }
            }

            if (showDialogNuevo) {
                NuevoUsuarioDialog(
                    onDismiss = { showDialogNuevo = false },
                    onUsuarioCreado = {
                        showDialogNuevo = false
                        fetchUsuarios()
                    }
                )
            }
        }
    }
}

@Composable
fun UserRowCard(user: UserItem, onUserUpdated: () -> Unit) {
    val statusColor = when (user.estatus) {
        1 -> Color(0xFF22C55E) // Activo
        2 -> Color(0xFFEAB308) // Inactivo
        3 -> Color(0xFFEF4444) // Suspensión
        else -> Color.Gray
    }

    val roleLabel = if (user.rol == 1) "Admin" else "Repartidor"
    val initials = if (user.nombreCompleto.isNotBlank()) {
        user.nombreCompleto.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
    } else "U"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF1E3A8A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.nombreCompleto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                    }
                    Text("Tel: ${user.telefono} • Rol: $roleLabel", fontSize = 12.sp, color = Color.Gray)
                }
            }

            TextButton(onClick = { /* Edit user */ }) {
                Text("Editar", color = Color(0xFF2563EB), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevoUsuarioDialog(
    onDismiss: () -> Unit,
    onUsuarioCreado: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var rolSeleccionado by remember { mutableStateOf(2) } // 1 = Admin, 2 = Repartidor
    var dropdownExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Nuevo Usuario", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nombre completo", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    placeholder = { Text("Ej. Sara López") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Teléfono", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    placeholder = { Text("Ej. 555-004-0004") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Contraseña", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    placeholder = { Text("••••••••") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Rol de usuario", fontSize = 12.sp, color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = if (rolSeleccionado == 1) "Administrador" else "Repartidor",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Repartidor (Rol 2)") },
                            onClick = {
                                rolSeleccionado = 2
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Administrador (Rol 1)") },
                            onClick = {
                                rolSeleccionado = 1
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nombre.isBlank() || telefono.isBlank() || contrasena.isBlank()) {
                        errorMsg = "Completa todos los campos obligatorios"
                        return@Button
                    }
                    isLoading = true
                    errorMsg = ""
                    thread {
                        try {
                            val body = JSONObject().apply {
                                put("nombre_completo", nombre.trim())
                                put("telefono", telefono.trim())
                                put("contrasena", contrasena)
                                put("rol", rolSeleccionado)
                                put("estatus", 1)
                            }.toString()

                            val url = java.net.URL("${ServerConfig.BASE_URL}/api/usuarios")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.connectTimeout = 5000
                            conn.doOutput = true
                            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                            if (conn.responseCode == 201) {
                                onUsuarioCreado()
                            } else {
                                val err = conn.errorStream?.bufferedReader()?.readText() ?: "Error"
                                val errJson = try { JSONObject(err) } catch (e: Exception) { null }
                                errorMsg = errJson?.optString("error") ?: "Error al crear usuario"
                            }
                        } catch (e: Exception) {
                            errorMsg = "Error de red: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A6B))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("Guardar usuario")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
