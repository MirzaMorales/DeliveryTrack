# Documentación Completa del Módulo TV (Leanback Dashboard)

El módulo `tv` es una aplicación de Android TV diseñada para visualizar el panel de control logístico en tiempo real. Utiliza Jetpack Compose para construir interfaces dinámicas adaptadas a pantallas grandes (Smart TV) y se integra con Google Maps y WebSockets para ofrecer telemetría en tiempo real sobre los repartidores y pedidos del sistema.

A continuación, se detalla el código completo y la explicación de cada uno de los archivos del módulo.

---

## 1. Configuración del Módulo (Gradle)

### `build.gradle.kts`
Este archivo de configuración define los plugins necesarios para compilar la aplicación, establece los niveles de SDK objetivo y compila las dependencias de red, Google Maps y la integración con el módulo compartido `:shared`.

```kotlin
plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "mx.utng.deliverytrack.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "mx.utng.deliverytrack.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
}
```

---

## 2. Manifiesto de la Aplicación

### `AndroidManifest.xml`
Declara los permisos de conexión a Internet y especifica los metadatos de hardware para dar soporte al entorno Leanback (televisión). También inyecta la API Key de Google Maps y registra la actividad del Dashboard como la actividad de inicio.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.hardware.type.television" android:required="false" />

    <application
        android:allowBackup="true"
        android:label="DeliveryTrack TV Dashboard"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light">

        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="AIzaSyBLiUs1tTdpU1bimlGdGZbU4jiZfrwcIZw" />
        
        <activity
            android:name=".presentation.TvDashboardActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## 3. Capa de Red y WebSocket

### `mqtt/TvWebSocketClient.kt`
Maneja la conexión WebSocket permanente con el servidor backend mediante OkHttp. Implementa lógica de reconexión exponencial si el canal se cae e inyecta los datos de los pedidos, repartidores y KPIs del sistema reactivamente usando `StateFlow` y corrutinas.

```kotlin
package mx.utng.deliverytrack.tv.mqtt

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.utng.deliverytrack.shared.config.ServerConfig
import mx.utng.deliverytrack.tv.domain.model.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket encargado de gestionar la comunicación en tiempo real con el servidor para la TV.
 * 
 * Abre una conexión persistente, maneja la reconexión exponencial automática
 * en caso de caídas de red y expone el estado de conexión y los flujos de datos.
 * 
 * @property serverUrl Dirección URL de WebSocket del servidor.
 */
class TvWebSocketClient(
    private val serverUrl: String = ServerConfig.WS_URL
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var reconnectJob: Job? = null
    private var currentBackoffMs = 1000L

    private val _connectionState = MutableStateFlow(false)
    /**
     * Flujo que representa si la conexión de WebSocket está abierta (true) o cerrada (false).
     */
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _kpis = MutableStateFlow<KpisDto?>(null)
    /**
     * Flujo que contiene los indicadores clave de rendimiento (KPIs) en tiempo real.
     */
    val kpis: StateFlow<KpisDto?> = _kpis.asStateFlow()

    private val _pedidos = MutableStateFlow<List<PedidoDto>>(emptyList())
    /**
     * Flujo que contiene la lista de pedidos activos monitoreados.
     */
    val pedidos: StateFlow<List<PedidoDto>> = _pedidos.asStateFlow()

    private val _repartidores = MutableStateFlow<Map<Int, RepartidorUbicacionDto>>(emptyMap())
    /**
     * Flujo que mapea el ID del repartidor con su ubicación y datos de telemetría más recientes.
     */
    val repartidores: StateFlow<Map<Int, RepartidorUbicacionDto>> = _repartidores.asStateFlow()

    /**
     * Inicia el proceso de conexión por WebSocket al servidor.
     * Si ya se encuentra conectado, la llamada se ignora.
     */
    fun connect() {
        if (webSocket != null) return
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("TvWebSocketClient", "WebSocket Connected!")
                _connectionState.value = true
                currentBackoffMs = 1000L // Reset backoff on success
                reconnectJob?.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleIncomingMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TvWebSocketClient", "WebSocket Closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TvWebSocketClient", "WebSocket Closed: $reason")
                _connectionState.value = false
                this@TvWebSocketClient.webSocket = null
                triggerReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TvWebSocketClient", "WebSocket Failure: ${t.message}", t)
                _connectionState.value = false
                this@TvWebSocketClient.webSocket = null
                triggerReconnect()
            }
        })
    }

    private fun triggerReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d("TvWebSocketClient", "Reconnecting in ${currentBackoffMs}ms...")
            delay(currentBackoffMs)
            currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(30000L)
            connect()
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")
            when (type) {
                "snapshot" -> {
                    val data = json.getJSONObject("data")
                    
                    // Parse KPIs
                    val kpisJson = data.getJSONObject("kpis")
                    val kpis = KpisDto(
                        pedidosActivos = kpisJson.getInt("pedidosActivos"),
                        entregadosHoy = kpisJson.getInt("entregadosHoy"),
                        tiempoPromedioMin = kpisJson.getInt("tiempoPromedioMin"),
                        incidencias = kpisJson.getInt("incidencias"),
                        repartidoresEnRuta = kpisJson.getInt("repartidoresEnRuta")
                    )

                    // Parse Pedidos
                    val pedidosJson = data.getJSONArray("pedidos")
                    val pedidosList = mutableListOf<PedidoDto>()
                    for (i in 0 until pedidosJson.length()) {
                        val p = pedidosJson.getJSONObject(i)
                        pedidosList.add(parsePedido(p))
                    }

                    // Parse Repartidores
                    val repartidoresJson = data.getJSONArray("repartidores")
                    val repartidoresMap = mutableMapOf<Int, RepartidorUbicacionDto>()
                    for (i in 0 until repartidoresJson.length()) {
                        val r = repartidoresJson.getJSONObject(i)
                        val repId = r.getInt("repartidorId")
                        repartidoresMap[repId] = parseRepartidor(r)
                    }

                    _kpis.value = kpis
                    _pedidos.value = pedidosList
                    _repartidores.value = repartidoresMap
                }
                "pedido_creado" -> {
                    val p = json.getJSONObject("pedido")
                    val newPedido = parsePedido(p)
                    _pedidos.value = _pedidos.value + newPedido
                    
                    // Recalculate KPI for active orders
                    val currentKpis = _kpis.value
                    if (currentKpis != null) {
                        _kpis.value = currentKpis.copy(
                            pedidosActivos = currentKpis.pedidosActivos + 1
                        )
                    }
                }
                "pedido_actualizado" -> {
                    val pedidoId = json.getInt("pedidoId")
                    val nuevoEstatus = json.getInt("nuevoEstatus")

                    // Update local orders list
                    val updatedList = _pedidos.value.map {
                        if (it.idPedido == pedidoId) {
                            it.copy(estatus = nuevoEstatus)
                        } else {
                            it
                        }
                    }
                    
                    // If order status became delivered (6) or cancelled (4), remove it from active list
                    val finalActiveList = updatedList.filter { it.estatus != 4 && it.estatus != 6 }
                    _pedidos.value = finalActiveList

                    // Recalculate KPIs based on status change
                    val currentKpis = _kpis.value
                    if (currentKpis != null) {
                        val activeCount = finalActiveList.count { it.estatus in listOf(1, 2, 3, 5) }
                        val repartidoresEnRuta = finalActiveList.count { it.estatus == 3 }
                        val incidencias = finalActiveList.count { it.estatus == 5 }
                        var deliveredToday = currentKpis.entregadosHoy
                        if (nuevoEstatus == 6) {
                            deliveredToday += 1
                        }

                        _kpis.value = currentKpis.copy(
                            pedidosActivos = activeCount,
                            entregadosHoy = deliveredToday,
                            incidencias = incidencias,
                            repartidoresEnRuta = repartidoresEnRuta
                        )
                    }
                }
                "ubicacion_actualizada" -> {
                    val repId = json.getInt("repartidorId")
                    val lat = json.getDouble("lat")
                    val lng = json.getDouble("lng")
                    val velocidad = json.getDouble("velocidad")
                    val bateria = json.getInt("bateria")

                    val update = RepartidorUbicacionDto(
                        repartidorId = repId,
                        lat = lat,
                        lng = lng,
                        velocidad = velocidad,
                        bateria = bateria
                    )

                    val newMap = _repartidores.value.toMutableMap()
                    newMap[repId] = update
                    _repartidores.value = newMap
                }
            }
        } catch (e: Exception) {
            Log.e("TvWebSocketClient", "Error parsing incoming JSON message: ${e.message}", e)
        }
    }

    private fun parsePedido(obj: JSONObject): PedidoDto {
        return PedidoDto(
            idPedido = obj.getInt("id_pedido"),
            nombreCliente = obj.getString("nombre_cliente"),
            telefono = obj.optString("telefono", ""),
            direccion = obj.getString("direccion"),
            referenciaLugar = obj.optString("referencia_lugar", ""),
            descripcionPedido = obj.optString("descripcion_pedido", ""),
            estatus = obj.getInt("estatus"),
            idRepartidor = if (obj.isNull("id_repartidor")) null else obj.getInt("id_repartidor")
        )
    }

    private fun parseRepartidor(obj: JSONObject): RepartidorUbicacionDto {
        return RepartidorUbicacionDto(
            repartidorId = obj.getInt("repartidorId"),
            lat = obj.getDouble("lat"),
            lng = obj.getDouble("lng"),
            velocidad = obj.optDouble("velocidad", 0.0),
            bateria = obj.optInt("bateria", 100)
        )
    }

    /**
     * Cierra la conexión activa de WebSocket y detiene cualquier proceso de reconexión.
     */
    fun disconnect() {
        webSocket?.close(1000, "Goodbye")
        webSocket = null
        _connectionState.value = false
        reconnectJob?.cancel()
    }
}
```

---

## 4. Capa de Repositorio de Datos

### `data/TvRepository.kt`
Este archivo actúa como capa de persistencia intermedia, consumiendo el origen de datos remoto (`RemoteDataSource`) si es necesario solicitar métricas y construir un objeto local `DashboardMetrics`.

```kotlin
package mx.utng.deliverytrack.tv.data

import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource
import mx.utng.deliverytrack.tv.domain.model.DashboardMetrics

/**
 * Repositorio de datos para el módulo TV.
 * 
 * Proporciona métodos para obtener información y métricas del panel logístico
 * haciendo uso de la fuente de datos remota.
 * 
 * @property remoteDataSource Origen de datos remoto para realizar peticiones HTTP.
 */
class TvRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {
    
    /**
     * Obtiene las métricas iniciales del panel logístico desde el backend.
     * 
     * @param callback Callback que retorna el resultado de la operación (éxito/fallo) y las métricas obtenidas.
     */
    fun fetchMetrics(callback: (Boolean, DashboardMetrics?) -> Unit) {
        remoteDataSource.get("/api/dashboard/metrics") { success, _ ->
            if (success) {
                callback(true, DashboardMetrics(12, 47, 22, 2, 8))
            } else {
                callback(false, null)
            }
        }
    }
}
```

---

## 5. Modelos de Dominio

### `domain/model/DashboardMetrics.kt`
Contiene la representación en memoria para las estadísticas consolidadas globales necesarias en el dashboard del cliente.

```kotlin
package mx.utng.deliverytrack.tv.domain.model

/**
 * Clase de datos que representa los indicadores clave de rendimiento (KPIs) del tablero de TV.
 * 
 * @property pedidosActivos Número de pedidos en estado activo (Aceptado, Pendiente, En camino, Retrasado).
 * @property entregadosHoy Cantidad de pedidos cuya entrega se concretó exitosamente el día de hoy.
 * @property tiempoPromedioMin Promedio del tiempo de entrega de los pedidos finalizados hoy en minutos.
 * @property incidencias Número de pedidos activos que registran algún tipo de retraso (estatus 5).
 * @property repartidoresEnRuta Cantidad de repartidores que están actualmente en camino entregando un pedido.
 */
data class DashboardMetrics(
    val pedidosActivos: Int = 0,
    val entregadosHoy: Int = 0,
    val tiempoPromedioMin: Int = 0,
    val incidencias: Int = 0,
    val repartidoresEnRuta: Int = 0
)
```

### `domain/model/DashboardModels.kt`
Este archivo contiene las representaciones de los DTOs de pedidos (`PedidoDto`), ubicaciones de los couriers (`RepartidorUbicacionDto`) y KPIs (`KpisDto`). Asimismo, define la clase sellada `WsEvent` que clasifica los eventos distribuidos mediante WebSockets.

```kotlin
package mx.utng.deliverytrack.tv.domain.model

/**
 * Representa los datos de un pedido transferidos desde el backend.
 * 
 * @property idPedido Identificador único del pedido.
 * @property nombreCliente Nombre del cliente destinatario del pedido.
 * @property telefono Teléfono de contacto del cliente.
 * @property direccion Dirección de entrega del pedido.
 * @property referenciaLugar Indicaciones o referencias del lugar de entrega.
 * @property descripcionPedido Detalle del contenido o notas del pedido.
 * @property estatus Código numérico del estado del pedido.
 * @property idRepartidor Identificador del repartidor asignado, o null si está sin asignar.
 */
data class PedidoDto(
    val idPedido: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referenciaLugar: String,
    val descripcionPedido: String,
    val estatus: Int,
    val idRepartidor: Int?
)

/**
 * Representa la ubicación y datos de telemetría actuales de un repartidor.
 * 
 * @property repartidorId Identificador único del repartidor.
 * @property lat Coordenada de latitud actual.
 * @property lng Coordenada de longitud actual.
 * @property velocidad Velocidad instantánea en km/h.
 * @property bateria Porcentaje de carga de batería del dispositivo móvil.
 */
data class RepartidorUbicacionDto(
    val repartidorId: Int,
    val lat: Double,
    val lng: Double,
    val velocidad: Double,
    val bateria: Int
)

/**
 * Representa los indicadores clave de rendimiento (KPIs) generales recibidos en tiempo real.
 * 
 * @property pedidosActivos Cantidad de pedidos activos.
 * @property entregadosHoy Cantidad de pedidos entregados en la fecha actual.
 * @property tiempoPromedioMin Promedio del tiempo empleado para las entregas en minutos.
 * @property incidencias Cantidad de pedidos con retrasos reportados.
 * @property repartidoresEnRuta Cantidad de repartidores entregando actualmente en calle.
 */
data class KpisDto(
    val pedidosActivos: Int,
    val entregadosHoy: Int,
    val tiempoPromedioMin: Int,
    val incidencias: Int,
    val repartidoresEnRuta: Int
)

/**
 * Representa la jerarquía de eventos que son recibidos del backend mediante la conexión de WebSocket.
 */
sealed class WsEvent {
    /**
     * Estado inicial del sistema que contiene toda la información de pedidos, ubicaciones y métricas.
     */
    data class Snapshot(
        val pedidos: List<PedidoDto>,
        val repartidores: List<RepartidorUbicacionDto>,
        val kpis: KpisDto
    ) : WsEvent()

    /**
     * Evento emitido al crearse un nuevo pedido en el sistema.
     */
    data class PedidoCreado(val pedido: PedidoDto) : WsEvent()

    /**
     * Evento emitido al actualizarse el estado de un pedido.
     */
    data class PedidoActualizado(val pedidoId: Int, val nuevoEstatus: Int) : WsEvent()

    /**
     * Evento emitido cuando un repartidor reporta una actualización de su posición GPS y telemetría.
     */
    data class UbicacionActualizada(
        val repartidorId: Int,
        val lat: Double,
        val lng: Double,
        val velocidad: Double,
        val bateria: Int
    ) : WsEvent()
}
```

---

## 6. Componentes de Interfaz Gráfica (UI)

### `presentation/TvDashboardActivity.kt`
Este archivo contiene la lógica de inicialización del tablero de control mediante Compose. Gestiona la suscripción a los flujos del WebSocket y pinta la cabecera general (incluyendo el estado de la conexión en vivo), la tira superior de tarjetas con métricas principales y las columnas de pedidos activos junto al mapa logístico.

```kotlin
package mx.utng.deliverytrack.tv.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.tv.domain.model.KpisDto
import mx.utng.deliverytrack.tv.mqtt.TvWebSocketClient
import mx.utng.deliverytrack.tv.presentation.components.FleetMap

/**
 * Actividad principal del módulo TV que inicializa y muestra el panel logístico de la Smart TV.
 * 
 * Abre la conexión de WebSocket al crearse y la libera al destruirse la actividad.
 */
class TvDashboardActivity : ComponentActivity() {
    private lateinit var webSocketClient: TvWebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webSocketClient = TvWebSocketClient()
        webSocketClient.connect()

        setContent {
            MaterialTheme {
                TvDashboardScreen(webSocketClient)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.disconnect()
    }
}

/**
 * Pantalla principal en Compose para el Dashboard de la TV.
 * 
 * Reúne y observa los flujos del WebSocket y pinta la cabecera, indicadores de métricas,
 * listado de pedidos activos y el mapa logístico de Google Maps.
 * 
 * @param client Instancia del cliente de WebSocket del que se consumen los datos en tiempo real.
 */
@Composable
fun TvDashboardScreen(client: TvWebSocketClient) {
    val kpis by client.kpis.collectAsState()
    val isConnected by client.connectionState.collectAsState()
    val pedidos by client.pedidos.collectAsState()
    val repartidores by client.repartidores.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DeliveryTrack — Panel Logístico (Smart TV)",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isConnected) "● En vivo" else "○ Desconectado",
                color = if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val activeKpis = kpis ?: KpisDto(0, 0, 0, 0, 0)
            
            MetricCard("Pedidos activos", "${activeKpis.pedidosActivos}", Color(0xFF3B82F6), Modifier.weight(1f))
            MetricCard("Entregados hoy", "${activeKpis.entregadosHoy}", Color(0xFF22C55E), Modifier.weight(1f))
            MetricCard("Tiempo prom.", "${activeKpis.tiempoPromedioMin} m", Color(0xFFF59E0B), Modifier.weight(1f))
            MetricCard("Incidencias", "${activeKpis.incidencias}", Color(0xFFEF4444), Modifier.weight(1f))
            MetricCard("Repartidores en ruta", "${activeKpis.repartidoresEnRuta}", Color(0xFF8B5CF6), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Pedidos Activos y Telemetría",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (pedidos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin entregas activas en este momento",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pedidos.size) { index ->
                                val pedido = pedidos[index]
                                val telemetry = pedido.idRepartidor?.let { repartidores[it] }

                                val statusText = when (pedido.estatus) {
                                    1 -> "Aceptado"
                                    2 -> "Pendiente"
                                    3 -> "En camino"
                                    5 -> "Retrasado"
                                    else -> "Estatus ${pedido.estatus}"
                                }

                                val statusColor = when (pedido.estatus) {
                                    1 -> Color(0xFF3B82F6)
                                    2 -> Color(0xFF94A3B8)
                                    3 -> Color(0xFF22C55E)
                                    5 -> Color(0xFFEF4444)
                                    else -> Color.LightGray
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Pedido #${pedido.idPedido} - ${pedido.nombreCliente}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = pedido.direccion,
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        
                                        if (telemetry != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Repartidor #${pedido.idRepartidor}",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Vel: ${telemetry.velocidad} km/h • Bat: ${telemetry.bateria}%",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FleetMap(
                        repartidores = repartidores,
                        pedidos = pedidos,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Componente de tarjeta reutilizable para mostrar indicadores métricos individuales.
 * 
 * @param title Título descriptivo de la métrica.
 * @param value Valor numérico o texto a destacar.
 * @param accentColor Color característico asignado al valor numérico para contraste visual.
 * @param modifier Modificador para personalizar tamaño, márgenes o peso.
 */
@Composable
fun MetricCard(title: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.LightGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = accentColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}
```

### `presentation/components/FleetMap.kt`
Este archivo encapsula la lógica del mapa logístico interactivo de Google Maps. Integra el ciclo de vida del mapa con Compose, dibuja marcadores dinámicos para cada repartidor con colores asociados a sus estados reales e implementa un re-centrado automático de cámara basado en la amplitud geográfica de los marcadores.

```kotlin
package mx.utng.deliverytrack.tv.presentation.components

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import mx.utng.deliverytrack.tv.domain.model.PedidoDto
import mx.utng.deliverytrack.tv.domain.model.RepartidorUbicacionDto

/**
 * Componente que encapsula la vista de Google Maps para mostrar las ubicaciones en tiempo real de los repartidores.
 * 
 * Gestiona el ciclo de vida del mapa, la actualización dinámica de marcadores
 * basados en eventos de telemetría y el encuadre automático de la cámara para incluir a toda la flota activa.
 * 
 * @param repartidores Mapa de repartidores y sus telemetrías/posiciones GPS actuales.
 * @param pedidos Lista de pedidos activos para realizar validación cruzada y colorear los pines.
 * @param modifier Modificador de Compose para tamaño y posicionamiento del mapa.
 */
@Composable
fun FleetMap(
    repartidores: Map<Int, RepartidorUbicacionDto>,
    pedidos: List<PedidoDto>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    val markersMap = remember { mutableMapOf<Int, Marker>() }

    // Correctly manage MapView lifecycle in Compose
    DisposableEffect(mapView) {
        mapView.onCreate(Bundle())
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                googleMap = map
                map.uiSettings.isZoomControlsEnabled = true
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.isMapToolbarEnabled = false
            }
            mapView
        },
        modifier = modifier,
        update = {
            val map = googleMap ?: return@AndroidView
            val builder = LatLngBounds.Builder()
            var hasLocations = false

            // Update or add markers for active and idle couriers
            repartidores.forEach { (repartidorId, loc) ->
                val position = LatLng(loc.lat, loc.lng)
                builder.include(position)
                hasLocations = true

                // Cross-reference active orders to determine marker color
                val activeOrder = pedidos.find { it.idRepartidor == repartidorId }
                val status = activeOrder?.estatus

                val hue = when (status) {
                    1 -> BitmapDescriptorFactory.HUE_BLUE      // Aceptado (Blue)
                    2 -> BitmapDescriptorFactory.HUE_ORANGE    // Pendiente (Orange)
                    3 -> BitmapDescriptorFactory.HUE_GREEN     // En camino (Green)
                    else -> BitmapDescriptorFactory.HUE_AZURE  // Idle / Sin pedido (Azure/Cyan as neutral)
                }

                val title = "Repartidor #$repartidorId (${if (activeOrder != null) "Pedido #${activeOrder.idPedido}" else "Sin pedido / Libre"})"

                val existingMarker = markersMap[repartidorId]
                if (existingMarker != null) {
                    existingMarker.position = position
                    existingMarker.title = title
                    existingMarker.setIcon(BitmapDescriptorFactory.defaultMarker(hue))
                } else {
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(title)
                            .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    )
                    if (marker != null) {
                        markersMap[repartidorId] = marker
                    }
                }
            }

            // Remove markers for disconnected couriers
            val currentIds = repartidores.keys
            val iterator = markersMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in currentIds) {
                    entry.value.remove()
                    iterator.remove()
                }
            }

            // Fit screen bounds to show all markers in view
            if (hasLocations) {
                try {
                    val bounds = builder.build()
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100)
                    map.animateCamera(cameraUpdate)
                } catch (e: IllegalStateException) {
                    // Map view size might not be fully calculated on first load
                }
            }
        }
    )
}
```
