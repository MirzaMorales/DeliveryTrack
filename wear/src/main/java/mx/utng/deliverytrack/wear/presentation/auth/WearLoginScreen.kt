package mx.utng.deliverytrack.wear.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
        TransformingLazyColumn(
            contentPadding = contentPadding,
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 0.dp)) {
                    Text(
                        text = "🚚 DeliveryTrack",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    text = "Inicio de Sesión",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Compact Teléfono Field
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = "Teléfono",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (telefono.isEmpty()) {
                            Text(
                                text = "Ej. 5551234567",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        BasicTextField(
                            value = telefono,
                            onValueChange = { telefono = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Compact Contraseña Field
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = "Contraseña",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (contrasena.isEmpty()) {
                            Text(
                                text = "••••••••",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        BasicTextField(
                            value = contrasena,
                            onValueChange = { contrasena = it },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (errorMessage.isNotEmpty()) {
                item {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFEF4444),
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
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
                                        errorMessage = "Acceso exclusivo para repartidores"
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
                                    errorMessage = errObj?.optString("error") ?: "Credenciales incorrectas"
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(horizontal = 14.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            text = "Iniciar sesión",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
