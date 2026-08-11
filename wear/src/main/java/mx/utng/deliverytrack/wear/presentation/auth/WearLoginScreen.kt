package mx.utng.deliverytrack.wear.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Modelo de datos que almacena los detalles de sesión del repartidor autenticado.
 *
 * @property idUser Identificador único del usuario (repartidor) en la base de datos.
 * @property nombreCompleto Nombre y apellido completo del repartidor.
 * @property telefono Teléfono de contacto registrado.
 */
data class WearCourierSession(
    val idUser: Int,
    val nombreCompleto: String,
    val telefono: String
)

/**
 * Función auxiliar para omitir la validación de certificados SSL de la conexión HTTPS.
 * Útil exclusivamente en entornos de desarrollo local donde el backend utiliza certificados autofirmados.
 *
 * @param conn Conexión HTTP sobre la que se aplicará el bypass de seguridad SSL.
 */
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

/**
 * Componente Composable que pinta la pantalla de inicio de sesión optimizada para WearOS.
 *
 * Presenta campos de entrada adaptados para pantallas circulares pequeñas donde el repartidor
 * introduce su teléfono y contraseña. Realiza peticiones directas HTTP POST al backend para verificar
 * las credenciales y asegurar que el usuario tenga el rol de repartidor (rol = 2) asignado.
 *
 * @param onLoginSuccess Callback invocado tras un inicio de sesión exitoso, retornando los detalles de la sesión.
 */
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
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            // Cabecera con título del sistema
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "DeliveryTrack",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Acceso Repartidores",
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Campo de entrada para Teléfono
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "TELÉFONO",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (telefono.isEmpty()) {
                            Text(
                                text = "Ej. 4181234567",
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Campo de entrada para Contraseña
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "CONTRASEÑA",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Alerta visual en caso de error
            if (errorMessage.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFCA5A5),
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Botón de Inicio de Sesión
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
                                applySslBypass(conn)

                                conn.requestMethod = "POST"
                                conn.setRequestProperty("Content-Type", "application/json")
                                conn.connectTimeout = 8000
                                conn.readTimeout = 8000
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
                        .height(36.dp)
                        .padding(horizontal = 14.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            text = "Iniciar sesión",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
