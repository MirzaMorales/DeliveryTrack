package mx.utng.deliverytrack.wear.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONObject
import kotlin.concurrent.thread

data class WearCourierSession(
    val idUser: Int,
    val nombreCompleto: String,
    val telefono: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearLoginScreen(
    onLoginSuccess: (WearCourierSession) -> Unit
) {
    var telefono by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "🚚 DeliveryTrack",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    text = "Acceso Repartidor",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Input Teléfono
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("Teléfono:", fontSize = 9.sp, color = Color.LightGray)
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        placeholder = { Text("Teléfono", fontSize = 10.sp, color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Input Contraseña
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("Contraseña:", fontSize = 9.sp, color = Color.LightGray)
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        placeholder = { Text("••••••••", fontSize = 10.sp, color = Color.Gray) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1
                    )
                }
            }

            if (errorMessage.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                item {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFEF4444),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
            }

            item {
                Button(
                    onClick = {
                        if (telefono.isBlank() || contrasena.isBlank()) {
                            errorMessage = "Ingresa teléfono y contraseña"
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
                                    conn.errorStream?.bufferedReader()?.readText() ?: "Error de inicio de sesión"
                                }

                                isLoading = false

                                if (code == 200) {
                                    val json = JSONObject(responseText)
                                    val userObj = json.getJSONObject("user")
                                    val rol = userObj.getInt("rol")

                                    if (rol != 2) {
                                        errorMessage = "Acceso exclusivo para repartidores en el reloj"
                                        return@thread
                                    }

                                    val session = WearCourierSession(
                                        idUser = userObj.getInt("id_user"),
                                        nombreCompleto = userObj.getString("nombre_completo"),
                                        telefono = userObj.getString("telefono")
                                    )
                                    onLoginSuccess(session)
                                } else {
                                    val errObj = try { JSONObject(responseText) } catch (e: Exception) { null }
                                    errorMessage = errObj?.optString("error") ?: "Teléfono o contraseña incorrectos"
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error de conexión: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Iniciar sesión", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
