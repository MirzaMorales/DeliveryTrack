# Documentación del Módulo TV (Leanback Dashboard)

El módulo `tv` es una aplicación para Android TV diseñada para visualizar el panel de control logístico en tiempo real. Utiliza Jetpack Compose para construir interfaces dinámicas adaptadas a pantallas grandes (Smart TV) y se integra con Google Maps y WebSockets para ofrecer telemetría en tiempo real sobre los repartidores y pedidos del sistema.

---

## Configuración del Módulo (Gradle)

### `build.gradle.kts`
Archivo de configuración de Gradle que define el pipeline de compilación y las dependencias del módulo TV:
- **Plugins**: Carga el plugin de aplicación de Android, soporte para Kotlin Android y Compose.
- **Configuración de SDK**: Compila y apunta al SDK versión 36, con un SDK mínimo requerido de 26.
- **Dependencias principales**:
  - `project(":shared")`: Vincula el módulo compartido.
  - OkHttp (`okhttp`): Para la comunicación mediante WebSockets.
  - Play Services Maps: Para el mapa interactivo.

```kotlin
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

## Manifiesto de la Aplicación

### `AndroidManifest.xml`
Configuración principal de metadatos, permisos y actividades para Android TV:
- Declara el permiso `android.permission.INTERNET` para la conexión de red.
- Configura la característica `android.hardware.type.television` como no requerida para asegurar compatibilidad.
- Incorpora la API Key de Google Maps.
- Establece la categoría `LEANBACK_LAUNCHER` para lanzar la app en la interfaz de Android TV.

```xml
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

## Capa de Datos y Red

### `mqtt/TvWebSocketClient.kt`
Componente encargado de establecer y gestionar la conexión por WebSockets con el backend:
- Usa `OkHttpClient` para conectarse a la URL de WebSocket (`WS_URL`).
- Implementa un mecanismo de reconexión exponencial (Backoff) en caso de desconexión.
- Expone los flujos de datos (`StateFlow`) para mantener informada a la interfaz.

```kotlin
class TvWebSocketClient(
    private val serverUrl: String = ServerConfig.WS_URL
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    fun connect() {
        if (webSocket != null) return
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = true
                reconnectJob?.cancel()
            }
            // ...
        })
    }
}
```

### `data/TvRepository.kt`
Gestiona la adquisición de datos tradicionales a través del origen de datos remoto (`RemoteDataSource`) para métricas de diagnóstico complementarias.

```kotlin
class TvRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {
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

## Capa de Dominio

### `domain/model/DashboardMetrics.kt`
Representación de las estadísticas principales del dashboard en un objeto de transferencia de datos.

```kotlin
data class DashboardMetrics(
    val pedidosActivos: Int = 0,
    val entregadosHoy: Int = 0,
    val tiempoPromedioMin: Int = 0,
    val incidencias: Int = 0,
    val repartidoresEnRuta: Int = 0
)
```

### `domain/model/DashboardModels.kt`
Define las estructuras para el parseo de datos de la conexión WebSocket y la jerarquía de eventos que ocurren en tiempo real.

```kotlin
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

data class RepartidorUbicacionDto(
    val repartidorId: Int,
    val lat: Double,
    val lng: Double,
    val velocidad: Double,
    val bateria: Int
)
```

---

## Capa de Presentación

### `presentation/TvDashboardActivity.kt`
Controlador principal de la vista en televisión. Renderiza el panel logístico, escucha los cambios de estado del WebSocket y distribuye la información a los componentes de Compose.

```kotlin
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
}
```

### `presentation/components/FleetMap.kt`
Componente Compose que encapsula la vista de Google Maps (`MapView`) para pintar a los repartidores en ruta de forma fluida a medida que actualizan su posición GPS.

```kotlin
@Composable
fun FleetMap(
    repartidores: Map<Int, RepartidorUbicacionDto>,
    pedidos: List<PedidoDto>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                googleMap = map
            }
            mapView
        },
        modifier = modifier
    )
}
```
