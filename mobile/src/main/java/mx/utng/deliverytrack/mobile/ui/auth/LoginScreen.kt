package mx.utng.deliverytrack.mobile.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (UserSession) -> Unit
) {
    val context = LocalContext.current
    var telefono by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val bgDark = Color(0xFF0F172A)
    val cardDark = Color(0xFF1E293B)
    val primaryBlue = Color(0xFF1D4ED8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header / App Title
            Text(
                text = "🚚 DeliveryTrack",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sistema de gestión logística",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Card Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "NÚMERO DE TELÉFONO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        placeholder = { Text("Ej. 5551234567", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryBlue,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Text(
                        text = "CONTRASEÑA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        placeholder = { Text("••••••••", color = Color.Gray) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryBlue,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (telefono.isBlank() || contrasena.isBlank()) {
                                errorMessage = "Ingresa tu número de teléfono y contraseña"
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
                            .height(50.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Iniciar sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Acceso exclusivo para personal autorizado",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}
