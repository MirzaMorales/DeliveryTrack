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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val context = LocalContext.current
    var usuarios by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    var showDialogUser by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<UserItem?>(null) }
    var userToDelete by remember { mutableStateOf<UserItem?>(null) }

    val primaryBlue = Color(0xFF1A3A6B)

    fun fetchUsuarios() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/usuarios")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
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

    fun deleteUsuarioLogico(user: UserItem) {
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/usuarios/${user.idUser}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "DELETE"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, "Usuario suspendido exitosamente", Toast.LENGTH_SHORT).show()
                    }
                    fetchUsuarios()
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                        onClick = {
                            userToEdit = null
                            showDialogUser = true
                        },
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
                                UserRowCard(
                                    user = user,
                                    onEditClick = {
                                        userToEdit = user
                                        showDialogUser = true
                                    },
                                    onDeleteClick = {
                                        userToDelete = user
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // User Edit / Create Modal Dialog
            if (showDialogUser) {
                UsuarioFormDialog(
                    userToEdit = userToEdit,
                    onDismiss = {
                        showDialogUser = false
                        userToEdit = null
                    },
                    onSuccess = {
                        showDialogUser = false
                        userToEdit = null
                        fetchUsuarios()
                    }
                )
            }

            // Delete Confirmation Dialog
            userToDelete?.let { user ->
                AlertDialog(
                    onDismissRequest = { userToDelete = null },
                    title = { Text("Suspender / Eliminar Usuario", fontWeight = FontWeight.Bold) },
                    text = { Text("¿Estás seguro de que deseas eliminar a ${user.nombreCompleto}? Su cuenta cambiará a estatus de suspensión.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                deleteUsuarioLogico(user)
                                userToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Sí, eliminar", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { userToDelete = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserRowCard(
    user: UserItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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

            // Action Icons (Edit & Delete)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = Color(0xFF2563EB)
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuarioFormDialog(
    userToEdit: UserItem?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val isEditMode = (userToEdit != null)

    var nombre by remember { mutableStateOf(userToEdit?.nombreCompleto ?: "") }
    var telefono by remember { mutableStateOf(userToEdit?.telefono ?: "") }
    var contrasena by remember { mutableStateOf("") }
    var rolSeleccionado by remember { mutableStateOf(userToEdit?.rol ?: 2) } // 1 = Admin, 2 = Repartidor
    var dropdownExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) "Editar Usuario" else "Nuevo Usuario",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
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

                Text(
                    text = if (isEditMode) "Nueva contraseña (opcional)" else "Contraseña",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    placeholder = { Text(if (isEditMode) "Mantener contraseña actual" else "••••••••") },
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
                    if (nombre.isBlank() || telefono.isBlank() || (!isEditMode && contrasena.isBlank())) {
                        errorMsg = "Completa los campos obligatorios"
                        return@Button
                    }
                    isLoading = true
                    errorMsg = ""
                    thread {
                        try {
                            val body = JSONObject().apply {
                                put("nombre_completo", nombre.trim())
                                put("telefono", telefono.trim())
                                if (contrasena.isNotBlank()) {
                                    put("contrasena", contrasena)
                                }
                                put("rol", rolSeleccionado)
                                put("estatus", userToEdit?.estatus ?: 1)
                            }.toString()

                            val urlString = if (isEditMode) {
                                "${ServerConfig.BASE_URL}/api/usuarios/${userToEdit?.idUser}"
                            } else {
                                "${ServerConfig.BASE_URL}/api/usuarios"
                            }

                            val url = java.net.URL(urlString)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = if (isEditMode) "PUT" else "POST"
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.connectTimeout = 5000
                            conn.doOutput = true
                            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                            val code = conn.responseCode
                            if (code == 200 || code == 201) {
                                onSuccess()
                            } else {
                                val err = conn.errorStream?.bufferedReader()?.readText() ?: "Error"
                                val errJson = try { JSONObject(err) } catch (e: Exception) { null }
                                errorMsg = errJson?.optString("error") ?: "Error al guardar cambios"
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
                    Text(if (isEditMode) "Editar usuario" else "Guardar usuario")
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
