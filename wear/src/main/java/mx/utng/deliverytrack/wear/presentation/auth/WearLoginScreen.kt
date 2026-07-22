package mx.utng.deliverytrack.wear.presentation.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import kotlin.concurrent.thread

data class WearCourierItem(
    val idUser: Int,
    val nombreCompleto: String,
    val telefono: String
)

@Composable
fun WearLoginScreen(
    onCourierSelected: (WearCourierItem) -> Unit
) {
    var couriers by remember { mutableStateOf<List<WearCourierItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val defaultCouriers = remember {
        listOf(
            WearCourierItem(1, "Juan Pérez", "4771234567"),
            WearCourierItem(2, "Carlos Repartidor", "5551234567")
        )
    }

    LaunchedEffect(reloadTrigger) {
        isLoading = true
        hasError = false
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/usuarios/repartidores")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        WearCourierItem(
                            idUser = obj.getInt("id_user"),
                            nombreCompleto = obj.getString("nombre_completo"),
                            telefono = obj.getString("telefono")
                        )
                    }
                    if (list.isNotEmpty()) {
                        couriers = list
                    } else {
                        couriers = defaultCouriers
                    }
                } else {
                    hasError = true
                    couriers = defaultCouriers
                }
            } catch (e: Exception) {
                hasError = true
                couriers = defaultCouriers
            } finally {
                isLoading = false
            }
        }
    }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Iniciar Sesión Reloj",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (isLoading) {
                item {
                    Text(
                        text = "Cargando repartidores...",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            } else {
                item {
                    Text(
                        text = if (hasError) "Perfiles (modo offline):" else "Selecciona tu perfil:",
                        fontSize = 11.sp,
                        color = if (hasError) Color(0xFFEAB308) else Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(couriers.size) { index ->
                    val item = couriers[index]
                    Button(
                        onClick = { onCourierSelected(item) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = item.nombreCompleto,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (hasError) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    item {
                        Button(
                            onClick = { reloadTrigger++ },
                            modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("🔄 Reintentar conexión", fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
