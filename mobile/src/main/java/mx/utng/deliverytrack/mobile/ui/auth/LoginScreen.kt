package mx.utng.deliverytrack.mobile.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONObject
import kotlin.concurrent.thread

data class UserSession(
    val idUser: Int,
    val nombreCompleto: String,
    val telefono: String,
    val rol: Int,
    val estatus: Int
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (UserSession) -> Unit
) {
    val context = LocalContext.current
    var telefono by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val bgDark = Color(0xFF0F172A)
    val cardDark = Color(0xFF1E293B)
    val primaryBlue = Color(0xFF2563EB)
    val lightText = Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant Header Icon Avatar
            Surface(
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                color = primaryBlue.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, primaryBlue)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Title
            Text(
                text = "DeliveryTrack",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Sistema de Gestión y Telemetría",
                fontSize = 13.sp,
                color = lightText,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Modern Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardDark),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Iniciar Sesión",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Teléfono Input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "NÚMERO DE TELÉFONO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = lightText,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = telefono,
                            onValueChange = { telefono = it },
                            placeholder = { Text("Ej. 4181234567", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = primaryBlue
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryBlue,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // Contraseña Input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CONTRASEÑA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = lightText,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = contrasena,
                            onValueChange = { contrasena = it },
                            placeholder = { Text("••••••••", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = primaryBlue
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(
                                            id = if (passwordVisible) mx.utng.deliverytrack.mobile.R.drawable.ic_eye_off else mx.utng.deliverytrack.mobile.R.drawable.ic_eye
                                        ),
                                        contentDescription = "Mostrar u ocultar contraseña",
                                        tint = lightText,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryBlue,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    if (errorMessage.isNotEmpty()) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (telefono.isBlank() || contrasena.isBlank()) {
                                errorMessage = "Ingresa tu teléfono y contraseña para ingresar"
                                return@Button
                            }

                            isLoading = true
                            errorMessage = ""

                            thread {
                                try {
                                    val bodyJson = JSONObject().apply {
                                        put("telefono", telefono.trim())
                                        put("contrasena", contrasena)
                                    }.toString()

                                    val url = java.net.URL("${ServerConfig.BASE_URL}/api/auth/login")
                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                    applySslBypass(conn)
                                    conn.requestMethod = "POST"
                                    conn.setRequestProperty("Content-Type", "application/json")
                                    conn.connectTimeout = 6000
                                    conn.readTimeout = 6000
                                    conn.doOutput = true
                                    conn.outputStream.write(bodyJson.toByteArray(Charsets.UTF_8))

                                    val code = conn.responseCode
                                    val responseText = if (code == 200) {
                                        conn.inputStream.bufferedReader().readText()
                                    } else {
                                        conn.errorStream?.bufferedReader()?.readText() ?: "Error de autenticación"
                                    }

                                    isLoading = false

                                    if (code == 200) {
                                        val json = JSONObject(responseText)
                                        val userObj = json.getJSONObject("user")
                                        val session = UserSession(
                                            idUser = userObj.getInt("id_user"),
                                            nombreCompleto = userObj.getString("nombre_completo"),
                                            telefono = userObj.getString("telefono"),
                                            rol = userObj.getInt("rol"),
                                            estatus = userObj.getInt("estatus")
                                        )
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            Toast.makeText(context, "Bienvenido ${session.nombreCompleto}", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess(session)
                                        }
                                    } else {
                                        val errObj = try { JSONObject(responseText) } catch (e: Exception) { null }
                                        val errorMsg = errObj?.optString("error") ?: "Credenciales incorrectas"
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            errorMessage = errorMsg
                                        }
                                    }
                                } catch (e: Exception) {
                                    isLoading = false
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        errorMessage = "Error de conexión: ${e.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Ingresar al Sistema", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "DeliveryTrack • Acceso de Usuarios",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
