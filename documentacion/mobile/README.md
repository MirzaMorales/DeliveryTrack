# Documentación Completa del Módulo Mobile (Aplicación Móvil Android)

El módulo `mobile` es la aplicación móvil principal del sistema DeliveryTrack para teléfonos y tabletas Android. Está diseñado para gestionar los roles de **Administrador** y **Repartidor**, proporcionando herramientas para la autenticación de usuarios, creación y edición de pedidos, asignación de repartidores, seguimiento por GPS, mensajería en tiempo real mediante MQTT y sincronización automática con dispositivos Wear OS (relojes inteligentes).

La interfaz de usuario está construida en su totalidad utilizando **Jetpack Compose** y **Material Design 3**, siguiendo patrones modernos de arquitectura en Android.

A continuación, se detalla la estructura organizada por carpetas, con la explicación completa y el código fuente comentado de cada archivo del módulo.

---

## 1. Configuración del Módulo y Manifest (`mobile/`)

Esta sección contiene los archivos raíz de configuración del módulo móvil, donde se definen las dependencias del proyecto, plugins de compilación, niveles de SDK compatibles y la declaración de componentes del sistema Android (actividades, servicios y permisos).

---

### `mobile/build.gradle.kts`
**Funcionalidad:** Archivo de configuración de Gradle para el módulo móvil. Configura la aplicación como un ejecutable Android (`android.application`), habilita el compilador de Jetpack Compose, establece la compatibilidad con Java 11 y declara las dependencias esenciales como `:shared` (módulo común), Play Services Wearable (para comunicación con Wear OS), OkHttp3 (para llamadas HTTP REST) y Jetpack Compose BOM.

```kotlin
plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "mx.utng.deliverytrack.mobile"
    compileSdk = 36 // Nivel de SDK compilado objetivo (Android 14/15)

    defaultConfig {
        applicationId = "mx.utng.deliverytrack"
        minSdk = 26 // Compatible desde Android 8.0 (Oreo)
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Compatibilidad de bytecode Java 11
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Importación del módulo compartido central
    implementation(project(":shared"))
    
    // Servicios de comunicación con Wear OS (Google Play Services)
    implementation(libs.play.services.wearable)
    
    // Cliente HTTP asíncrono OkHttp3
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Core Android & Jetpack Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    
    // Librerías de componentes UI Material 3 para teléfonos/tabletas
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.material3:material3")
}
```

---

### `mobile/src/main/AndroidManifest.xml`
**Funcionalidad:** Manifest de la aplicación Android. Declara los permisos de red (`INTERNET`), las actividades que componen la interfaz (`MainActivity`, `NuevoPedidoActivity`, `GestionUsuariosActivity`, etc.), el tema sin Action Bar predeterminada y el servicio de escucha en segundo plano `MobileWearableListenerService` para comunicarse con la app de Wear OS mediante el intent filter `BIND_LISTENER`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permiso esencial para comunicación HTTP REST, WebSockets y MQTT -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="DeliveryTrack"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar"
        android:networkSecurityConfig="@xml/network_security_config">
        
        <!-- Actividad Principal (Punto de Entrada de la Aplicación) -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Formulario de Creación y Edición de Pedidos -->
        <activity
            android:name=".ui.NuevoPedidoActivity"
            android:exported="false" />

        <!-- Panel de Administración de Usuarios del Sistema -->
        <activity
            android:name=".ui.admin.GestionUsuariosActivity"
            android:exported="false" />

        <!-- Vista de Detalle y Seguimiento de Pedidos para Administradores -->
        <activity
            android:name=".ui.admin.AdminDetallePedidoActivity"
            android:exported="false" />

        <!-- Vista de Detalle y Actualización de Estatus para Repartidores -->
        <activity
            android:name=".ui.repartidor.DetallePedidoRepartidorActivity"
            android:exported="false" />

        <!-- Servicio en Segundo Plano para Sincronización con Wear OS -->
        <service
            android:name=".data.sync.MobileWearableListenerService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
            </intent-filter>
        </service>
        
    </application>

</manifest>
```

---

## 2. Carpeta Data - Modelos (`data/models`)

Esta carpeta almacena las clases de datos (`data class`) de Kotlin que representan las entidades del dominio de la aplicación. Estos modelos estructuran la información procesada en la API REST y transferida en la interfaz de usuario.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/data/models/Pedido.kt`
**Funcionalidad:** Modela la estructura de datos de un pedido dentro de la aplicación móvil, incluyendo los datos del cliente, la dirección, la referencia, la descripción del pedido, el ID del repartidor asignado y el estado numérico del pedido (1: Aceptado, 2: Pendiente, 3: En camino, 4: Cancelado, 5: Retrasado, 6: Entregado).

```kotlin
package mx.utng.deliverytrack.mobile.data.models

/**
 * Entidad de datos que representa un pedido en la plataforma móvil.
 *
 * @property id Identificador único del pedido en la base de datos central.
 * @property nombreCliente Nombre completo del cliente que realiza la compra.
 * @property telefono Teléfono de contacto directo del cliente.
 * @property direccion Dirección física de entrega.
 * @property referenciaLugar Indicaciones extra o referencias del domicilio.
 * @property descripcionPedido Detalle de los artículos o productos a entregar.
 * @property idRepartidor Identificador del repartidor asignado.
 * @property estatus Código numérico del estado del pedido.
 */
data class Pedido(
    val id: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referenciaLugar: String,
    val descripcionPedido: String,
    val idRepartidor: Int,
    val estatus: Int
)
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/data/models/Repartidor.kt`
**Funcionalidad:** Modela la entidad de un repartidor dentro del sistema móvil, almacenando sus datos básicos como ID de usuario, nombre completo y teléfono de contacto para su selección en menús desplegables.

```kotlin
package mx.utng.deliverytrack.mobile.data.models

/**
 * Representa a un repartidor disponible en la flotilla del sistema.
 *
 * @property id ID único de usuario del repartidor.
 * @property nombre Nombre completo del repartidor.
 * @property telefono Número de teléfono para contacto de coordinación.
 */
data class Repartidor(
    val id: Int,
    val nombre: String,
    val telefono: String
)
```

---

## 3. Carpeta Data - Repositorio (`data/repository`)

Esta carpeta contiene las clases encargadas del acceso y abstracción de datos remotos, procesando las respuestas JSON enviadas por la API REST del backend.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/data/repository/MobileRepository.kt`
**Funcionalidad:** Repositorio de datos del módulo móvil. Utiliza el cliente `RemoteDataSource` del módulo compartido `:shared` para realizar peticiones HTTP GET y mapear la respuesta JSON con la lista de repartidores registrados y activos en el sistema.

```kotlin
package mx.utng.deliverytrack.mobile.data.repository

import mx.utng.deliverytrack.mobile.data.models.Repartidor
import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositorio de datos para el módulo móvil.
 * Centraliza las consultas a endpoints de usuarios y repartidores.
 * 
 * @property remoteDataSource Instancia del origen de datos remoto del módulo compartido.
 */
class MobileRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {

    /**
     * Consulta el endpoint de repartidores y mapea el JSON de respuesta a una lista de objetos [Repartidor].
     * 
     * @param callback Retorna éxito (Boolean), lista de repartidores o mensaje de error.
     */
    fun getRepartidores(callback: (Boolean, List<Repartidor>?, String?) -> Unit) {
        remoteDataSource.get("/api/usuarios/repartidores") { success, response ->
            if (success && response != null) {
                try {
                    val arr = JSONArray(response)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        Repartidor(
                            id = obj.getInt("id_user"),
                            nombre = obj.getString("nombre_completo"),
                            telefono = obj.getString("telefono")
                        )
                    }
                    callback(true, list, null)
                } catch (e: Exception) {
                    callback(false, null, e.message)
                }
            } else {
                callback(false, null, response ?: "Error de carga")
            }
        }
    }
}
```

---

## 4. Carpeta Data - Sincronización (`data/sync`)

Esta carpeta administra la sincronización y puente de datos entre el dispositivo móvil (teléfono) y los relojes inteligentes Wear OS mediante el protocolo Google Wearable Message API.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/data/sync/MobileWearableListenerService.kt`
**Funcionalidad:** Servicio en segundo plano (`WearableListenerService`) de Google Play Services. Escucha mensajes entrantes desde la app instalada en el smartwatch (como peticiones de pedido activo `/pedido/activo/get` o actualizaciones de estado `/pedido/status/update`), consulta el backend REST del servidor y responde de vuelta al smartwatch con los datos en formato JSON.

```kotlin
package mx.utng.deliverytrack.mobile.data.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import mx.utng.deliverytrack.shared.config.ServerConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Servicio de escucha Wearable de Android.
 * 
 * Actúa como puente entre la aplicación del reloj Wear OS y el backend REST en la nube.
 */
class MobileWearableListenerService : WearableListenerService() {

    // Pool de hilos para ejecutar llamadas de red de forma asíncrona fuera del hilo principal
    private val executor = Executors.newCachedThreadPool()

    // Cliente OkHttp configurado con tiempos límite de 5 segundos
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "MobileWearableService"
    }

    /**
     * Invocado automáticamente cuando un reloj Wear OS emparejado envía un mensaje.
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val path = messageEvent.path
        val payload = String(messageEvent.data, Charsets.UTF_8)
        val sourceNodeId = messageEvent.sourceNodeId

        Log.d(TAG, "Mensaje recibido desde Wearable. Ruta: $path, Payload: $payload")

        // Delegar la petición al pool de hilos de ejecución
        executor.execute {
            try {
                when (path) {
                    "/pedido/activo/get" -> fetchActiveOrder(payload, sourceNodeId)
                    "/pedido/status/update" -> updateOrderStatus(payload, sourceNodeId)
                    else -> Log.w(TAG, "Ruta de mensaje no reconocida: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensaje en ruta: $path", e)
            }
        }
    }

    /**
     * Consulta el pedido activo asignado al repartidor desde la API REST y envía la respuesta al reloj.
     */
    private fun fetchActiveOrder(repartidorId: String, clientNodeId: String) {
        Log.d(TAG, "Consultando pedido activo para el repartidor ID: $repartidorId")
        val request = Request.Builder()
            .url("${ServerConfig.BASE_URL}/api/pedidos/activo?repartidorId=$repartidorId")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Respuesta del backend (Pedido Activo): Código=${response.code}, Cuerpo=$responseBody")
                
                val resultObj = JSONObject().apply {
                    put("success", response.isSuccessful)
                    put("code", response.code)
                    put("data", if (responseBody.isNotEmpty()) JSONObject(responseBody) else null)
                }
                
                sendReply(clientNodeId, "/pedido/activo/response", resultObj.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando pedido activo en backend", e)
            val errObj = JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
            sendReply(clientNodeId, "/pedido/activo/response", errObj.toString())
        }
    }

    /**
     * Actualiza el estado de un pedido enviando una petición PATCH al servidor REST.
     */
    private fun updateOrderStatus(jsonPayload: String, clientNodeId: String) {
        Log.d(TAG, "Actualizando estatus de pedido con payload: $jsonPayload")
        try {
            val json = JSONObject(jsonPayload)
            val orderId = json.getInt("id_pedido")
            val estatus = json.getInt("estatus")
            val repartidorId = json.getInt("repartidorId")

            val requestBodyJson = JSONObject().apply {
                put("estatus", estatus)
                put("repartidorId", repartidorId)
            }

            val request = Request.Builder()
                .url("${ServerConfig.BASE_URL}/api/pedidos/$orderId/estatus")
                .patch(requestBodyJson.toString().toRequestBody(mediaTypeJson))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Respuesta del backend (Actualización de Estado): Código=${response.code}, Cuerpo=$responseBody")

                val resultObj = JSONObject().apply {
                    put("success", response.isSuccessful)
                    put("code", response.code)
                    put("data", if (responseBody.isNotEmpty()) JSONObject(responseBody) else null)
                }

                sendReply(clientNodeId, "/pedido/status/response", resultObj.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estatus o parseando payload", e)
            val errObj = JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
            sendReply(clientNodeId, "/pedido/status/response", errObj.toString())
        }
    }

    /**
     * Envia el mensaje de respuesta de vuelta al nodo de Wear OS origen mediante MessageClient.
     */
    private fun sendReply(targetNodeId: String, path: String, response: String) {
        Log.d(TAG, "Enviando respuesta a nodo Wearable $targetNodeId en ruta $path. Payload: $response")
        Wearable.getMessageClient(this)
            .sendMessage(targetNodeId, path, response.toByteArray(Charsets.UTF_8))
            .addOnSuccessListener {
                Log.d(TAG, "Respuesta enviada exitosamente a la ruta: $path")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al enviar respuesta a la ruta: $path", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
```

---

## 5. Carpeta MQTT (`mqtt`)

Esta carpeta se encarga de gestionar la conexión con el broker de mensajería MQTT para transmitir la telemetría en tiempo real del repartidor (ubicación GPS y velocidad).

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/mqtt/MobileMqttManager.kt`
**Funcionalidad:** Gestor de conexión MQTT para el teléfono móvil. Se conecta al broker broker de HiveMQ Cloud vía SSL en el puerto 8883 y publica la telemetría del repartidor (`id_repartidor`, `lat`, `lng`, `speed`) en el tema `deliverytrack/telemetry/{id_repartidor}`.

```kotlin
package mx.utng.deliverytrack.mobile.mqtt

import mx.utng.deliverytrack.shared.mqtt.MqttClientHelper

/**
 * Gestor MQTT para transmisión de telemetría de posición GPS en tiempo real.
 * 
 * @property serverUri URI del broker MQTT con cifrado SSL (HiveMQ Cloud).
 * @property username Usuario de autenticación del broker MQTT.
 * @property password Contraseña de autenticación del broker MQTT.
 */
class MobileMqttManager(
    private val serverUri: String = "ssl://79a94522998842728c5ef7bf42fd3c30.s1.eu.hivemq.cloud:8883",
    private val username: String = "smarthealthmonitor",
    private val password: String = "linux123"
) {
    private var mqttClientHelper: MqttClientHelper? = null

    /**
     * Conecta con el broker MQTT utilizando un cliente ID específico.
     */
    fun connect(clientId: String, onConnected: () -> Unit) {
        mqttClientHelper = MqttClientHelper(serverUri, clientId, username, password)
        mqttClientHelper?.connect(onConnected) { _ -> }
    }

    /**
     * Publica las coordenadas GPS y velocidad del repartidor en el canal MQTT correspondiente.
     */
    fun publishTelemetry(courierId: Int, lat: Double, lng: Double, speed: Float) {
        val payload = """{"id_repartidor":$courierId,"lat":$lat,"lng":$lng,"speed":$speed}"""
        mqttClientHelper?.publish("deliverytrack/telemetry/$courierId", payload)
    }

    /**
     * Desconecta limpiamente la sesión MQTT activa.
     */
    fun disconnect() {
        mqttClientHelper?.disconnect()
    }
}
```

---

## 6. Carpeta Navegación (`navigation`)

Esta carpeta define las rutas de navegación tipadas para Compose Navigation dentro de la aplicación móvil.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/navigation/NavGraph.kt`
**Funcionalidad:** Sealed class `Screen` que define los nombres de ruta estáticos y dinámicos para la navegación entre pantallas (Login, Mis Pedidos, Detalle de Pedido, Dashboard de Admin, Nuevo Pedido y Gestión de Repartidores).

```kotlin
package mx.utng.deliverytrack.mobile.navigation

/**
 * Definición de pantallas y rutas de navegación de la app.
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object MisPedidos : Screen("mis_pedidos")
    object DetallePedido : Screen("detalle_pedido/{pedidoId}") {
        fun createRoute(pedidoId: Int) = "detalle_pedido/$pedidoId"
    }
    object AdminDashboard : Screen("admin_dashboard")
    object NuevoPedido : Screen("nuevo_pedido")
    object GestionRepartidores : Screen("gestion_repartidores")
}
```

---

## 7. Carpeta UI - Pantallas Principales (`ui`)

Esta carpeta contiene la actividad principal `MainActivity` y pantallas generales como la creación y edición de pedidos.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/MainActivity.kt`
**Funcionalidad:** Actividad raíz y punto de entrada de la aplicación móvil Android. Controla la sesión de usuario activa (`UserSession`) usando estado reactivo de Compose (`mutableStateOf`). Si no hay sesión, muestra `LoginScreen`. Si el rol es 1 (Administrador), carga `AdminDashboardScreen`. Si el rol es 2 (Repartidor), carga `MisPedidosRepartidorScreen`.

```kotlin
package mx.utng.deliverytrack.mobile.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mx.utng.deliverytrack.mobile.ui.admin.AdminDashboardScreen
import mx.utng.deliverytrack.mobile.ui.admin.GestionUsuariosActivity
import mx.utng.deliverytrack.mobile.ui.auth.LoginScreen
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
import mx.utng.deliverytrack.mobile.ui.repartidor.DetallePedidoRepartidorActivity
import mx.utng.deliverytrack.mobile.ui.repartidor.MisPedidosRepartidorScreen

/**
 * Actividad Principal de la aplicación móvil.
 * Controla el enrutamiento según el rol de usuario autenticado.
 */
class MainActivity : ComponentActivity() {

    // Estado reactivo que almacena la sesión de usuario activa
    private var activeSession by mutableStateOf<UserSession?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this

        setContent {
            MaterialTheme {
                val session = activeSession
                if (session == null) {
                    // Muestra pantalla de inicio de sesión si no hay sesión activa
                    LoginScreen(
                        onLoginSuccess = { user ->
                            activeSession = user
                        }
                    )
                } else if (session.rol == 1) { 
                    // Muestra el Dashboard para administradores (Rol 1)
                    AdminDashboardScreen(
                        userSession = session,
                        onCrearPedidoClick = {
                            context.startActivity(Intent(context, NuevoPedidoActivity::class.java))
                        },
                        onGestionUsuariosClick = {
                            context.startActivity(Intent(context, GestionUsuariosActivity::class.java))
                        },
                        onLogoutClick = {
                            activeSession = null
                        }
                    )
                } else { 
                    // Muestra la lista de entregas para repartidores (Rol 2)
                    MisPedidosRepartidorScreen(
                        userSession = session,
                        onVerDetalleClick = { pedidoId ->
                            val intent = Intent(context, DetallePedidoRepartidorActivity::class.java).apply {
                                putExtra(DetallePedidoRepartidorActivity.EXTRA_ORDER_ID, pedidoId)
                                putExtra(DetallePedidoRepartidorActivity.EXTRA_COURIER_ID, session.idUser)
                            }
                            context.startActivity(intent)
                        },
                        onLogoutClick = {
                            activeSession = null
                        }
                    )
                }
            }
        }
    }
}
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/NuevoPedidoActivity.kt`
**Funcionalidad:** Formulario interactivo en Jetpack Compose para crear un nuevo pedido o editar un pedido existente (`EXTRA_EDIT_ORDER_ID`). Carga dinámicamente la lista de repartidores en un menú desplegable `ExposedDropdownMenuBox`, valida campos obligatorios y envía peticiones POST (crear) o PUT (editar) a la API REST.

```kotlin
package mx.utng.deliverytrack.mobile.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.mobile.data.models.Repartidor
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Función de utilidad para omitir la validación estricta de certificados SSL en entornos de prueba.
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

class NuevoPedidoActivity : ComponentActivity() {

    private val backendUrl = ServerConfig.BASE_URL
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val EXTRA_EDIT_ORDER_ID = "extra_edit_order_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editOrderId = intent.getIntExtra(EXTRA_EDIT_ORDER_ID, -1)

        setContent {
            MaterialTheme {
                NuevoPedidoScreen(
                    backendUrl = backendUrl,
                    editOrderId = if (editOrderId > 0) editOrderId else null,
                    onPedidoGuardado = { finish() },
                    onShowToast = { msg ->
                        mainHandler.post {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevoPedidoScreen(
    backendUrl: String,
    editOrderId: Int? = null,
    onPedidoGuardado: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val isEditMode = (editOrderId != null)

    var nombreCliente by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var referencia by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    var repartidores by remember { mutableStateOf<List<Repartidor>>(emptyList()) }
    var repartidorSeleccionado by remember { mutableStateOf<Repartidor?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isLoadingRepartidores by remember { mutableStateOf(true) }
    var errorRepartidores by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF1A3A6B)
    val accentBlue = Color(0xFF2563EB)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Obtener la lista de repartidores disponibles
    LaunchedEffect(Unit) {
        thread {
            try {
                val url = java.net.URL("$backendUrl/api/usuarios/repartidores")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                val code = conn.responseCode
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(response)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        Repartidor(
                            id = obj.getInt("id_user"),
                            nombre = obj.getString("nombre_completo"),
                            telefono = obj.getString("telefono")
                        )
                    }
                    mainHandler.post {
                        repartidores = list
                        if (list.isNotEmpty() && repartidorSeleccionado == null) {
                            repartidorSeleccionado = list[0]
                        }
                        isLoadingRepartidores = false
                    }
                } else {
                    mainHandler.post {
                        errorRepartidores = "Error $code al cargar repartidores"
                        isLoadingRepartidores = false
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    errorRepartidores = "Error de red: ${e.message}"
                    isLoadingRepartidores = false
                }
            }
        }
    }

    // Cargar detalles del pedido si está en modo Edición
    LaunchedEffect(editOrderId) {
        if (editOrderId != null) {
            thread {
                try {
                    val url = java.net.URL("$backendUrl/api/pedidos/$editOrderId")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"

                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        val obj = JSONObject(text)
                        mainHandler.post {
                            nombreCliente = obj.optString("nombre_cliente", "")
                            telefono = obj.optString("telefono", "")
                            direccion = obj.optString("direccion", "")
                            referencia = obj.optString("referencia_lugar", "")
                            descripcion = obj.optString("descripcion_pedido", "")
                            val repId = obj.optInt("id_repartidor", -1)
                            if (repId > 0 && repartidores.isNotEmpty()) {
                                repartidores.find { it.id == repId }?.let {
                                    repartidorSeleccionado = it
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Editar Pedido #$editOrderId" else "Nuevo Pedido",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onPedidoGuardado) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditMode) "INFORMACIÓN DEL PEDIDO A EDITAR" else "DATOS DEL CLIENTE Y ENTREGA",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            // Campos del formulario
            OutlinedTextField(
                value = nombreCliente,
                onValueChange = { nombreCliente = it },
                label = { Text("Nombre del cliente *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono de contacto *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it },
                label = { Text("Dirección de entrega *") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = referencia,
                onValueChange = { referencia = it },
                label = { Text("Referencia del lugar") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción de los productos") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // Selector desplegable de repartidores
            Text(
                text = "REPARTIDOR ASIGNADO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            when {
                isLoadingRepartidores -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cargando repartidores disponibles...")
                    }
                }
                errorRepartidores.isNotEmpty() -> {
                    Text(errorRepartidores, color = Color.Red, fontSize = 13.sp)
                }
                repartidores.isEmpty() -> {
                    Text("No hay repartidores activos disponibles.", color = Color.Red, fontSize = 13.sp)
                }
                else -> {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = repartidorSeleccionado?.nombre ?: "Seleccionar repartidor",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            repartidores.forEach { rep ->
                                DropdownMenuItem(
                                    text = { Text("${rep.nombre} (${rep.telefono})") },
                                    onClick = {
                                        repartidorSeleccionado = rep
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón para Guardar / Editar Pedido
            Button(
                onClick = {
                    if (nombreCliente.isBlank() || telefono.isBlank() || direccion.isBlank() || repartidorSeleccionado == null) {
                        onShowToast("Completa los campos obligatorios y selecciona un repartidor")
                        return@Button
                    }

                    isLoading = true

                    thread {
                        try {
                            val body = JSONObject().apply {
                                put("nombre_cliente", nombreCliente.trim())
                                put("telefono", telefono.trim())
                                put("direccion", direccion.trim())
                                put("referencia_lugar", referencia.trim())
                                put("descripcion_pedido", descripcion.trim())
                                put("id_repartidor", repartidorSeleccionado?.id)
                            }.toString()

                            val urlString = if (isEditMode) "$backendUrl/api/pedidos/$editOrderId" else "$backendUrl/api/pedidos"
                            val url = java.net.URL(urlString)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            applySslBypass(conn)
                            conn.requestMethod = if (isEditMode) "PUT" else "POST"
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.connectTimeout = 6000
                            conn.doOutput = true
                            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                            val code = conn.responseCode
                            isLoading = false

                            if (code == 200 || code == 201) {
                                onShowToast(if (isEditMode) "Pedido #$editOrderId actualizado exitosamente" else "Pedido creado exitosamente")
                                mainHandler.post { onPedidoGuardado() }
                            } else {
                                onShowToast("Error $code al guardar pedido")
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            onShowToast("Error de conexión: ${e.message}")
                        }
                    }
                },
                enabled = !isLoading && repartidorSeleccionado != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isEditMode) "Editar pedido" else "Guardar pedido",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
```

---

## 8. Carpeta UI - Autenticación (`ui/auth`)

Esta carpeta contiene la pantalla de inicio de sesión y la definición del modelo de sesión de usuario.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/auth/LoginScreen.kt`
**Funcionalidad:** Pantalla moderna de inicio de sesión. Ofrece una interfaz en tono oscuro (`Color(0xFF0F172A)`), campos para el número de teléfono y contraseña con opción de visibilidad (icono de ojo), envío de credenciales a `/api/auth/login` y retorno de la sesión `UserSession` al ser autenticado con éxito.

```kotlin
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

/**
 * Modelo que almacena los datos de la sesión del usuario autenticado.
 */
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
            // Logotipo circular
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

            // Tarjeta de Formulario de Login
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

                    // Campo de Teléfono
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "NÚMERO DE TELÉFONO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = lightText
                        )
                        OutlinedTextField(
                            value = telefono,
                            onValueChange = { telefono = it },
                            placeholder = { Text("Ej. 4181234567", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = primaryBlue)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Campo de Contraseña
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CONTRASEÑA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = lightText
                        )
                        OutlinedTextField(
                            value = contrasena,
                            onValueChange = { contrasena = it },
                            placeholder = { Text("••••••••", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = primaryBlue)
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
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

                    // Botón de Ingreso
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
        }
    }
}
```

---

## 9. Carpeta UI - Panel de Administración (`ui/admin`)

Esta carpeta contiene las actividades y pantallas exclusivas para los usuarios con rol de Administrador.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/admin/AdminDashboardScreen.kt`
**Funcionalidad:** Panel de control de administración. Presenta un listado completo de pedidos activos, filtros dinámicos por estado y por repartidor asignado, refresco automático de pantalla al volver mediante `LifecycleEventObserver`, y capacidad de cancelar pedidos directamente.

```kotlin
package mx.utng.deliverytrack.mobile.ui.admin

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
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

data class AdminPedidoItem(
    val idPedido: Int,
    val nombreCliente: String,
    val direccion: String,
    val repartidorNombre: String,
    val estatus: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    userSession: UserSession,
    onCrearPedidoClick: () -> Unit,
    onGestionUsuariosClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var pedidos by remember { mutableStateOf<List<AdminPedidoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF1A3A6B)

    // Consulta de pedidos activos para administración
    fun fetchAdminPedidos() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/admin/activos")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        AdminPedidoItem(
                            idPedido = obj.getInt("id_pedido"),
                            nombreCliente = obj.getString("nombre_cliente"),
                            direccion = obj.getString("direccion"),
                            repartidorNombre = if (obj.isNull("repartidor_nombre")) "Sin asignar" else obj.getString("repartidor_nombre"),
                            estatus = obj.getInt("estatus")
                        )
                    }
                    pedidos = list
                } else {
                    errorMessage = "Error al cargar pedidos del sistema"
                }
            } catch (e: Exception) {
                errorMessage = "Error de conexión: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Cancelar un pedido activo
    fun cancelarPedido(orderId: Int) {
        thread {
            try {
                val body = JSONObject().apply {
                    put("estatus", 4) // Estatus 4: Cancelado
                }.toString()

                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                if (conn.responseCode == 200) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, "Pedido #$orderId cancelado", Toast.LENGTH_SHORT).show()
                    }
                    fetchAdminPedidos()
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Recarga automática al volver a la pantalla (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchAdminPedidos()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var selectedEstatusFilter by remember { mutableStateOf<Int?>(null) }
    var selectedRepartidorFilter by remember { mutableStateOf("Todos los repartidores") }
    var repartidorDropdownExpanded by remember { mutableStateOf(false) }

    val repartidoresDisponibles = remember(pedidos) {
        listOf("Todos los repartidores") + pedidos.map { it.repartidorNombre }.distinct().sorted()
    }

    val filteredPedidos = remember(pedidos, selectedEstatusFilter, selectedRepartidorFilter) {
        pedidos.filter { item ->
            val matchesStatus = (selectedEstatusFilter == null || item.estatus == selectedEstatusFilter)
            val matchesRepartidor = (selectedRepartidorFilter == "Todos los repartidores" ||
                                     item.repartidorNombre.equals(selectedRepartidorFilter, ignoreCase = true))
            matchesStatus && matchesRepartidor
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DeliveryTrack", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text("Gestión de Pedidos", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    }
                },
                actions = {
                    Button(
                        onClick = onCrearPedidoClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    IconButton(onClick = onLogoutClick) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    label = { Text("Pedidos") },
                    icon = { Icon(Icons.Default.List, contentDescription = "Pedidos") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onGestionUsuariosClick,
                    label = { Text("Usuarios") },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Usuarios") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Filtro desplegable por repartidor
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = { repartidorDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Repartidor: $selectedRepartidorFilter", fontSize = 13.sp, color = Color(0xFF1E293B))
                            Text("▼", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    DropdownMenu(
                        expanded = repartidorDropdownExpanded,
                        onDismissRequest = { repartidorDropdownExpanded = false }
                    ) {
                        repartidoresDisponibles.forEach { repNombre ->
                            DropdownMenuItem(
                                text = { Text(repNombre) },
                                onClick = {
                                    selectedRepartidorFilter = repNombre
                                    repartidorDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Lista de pedidos filtrada
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPedidos) { item ->
                        AdminPedidoRow(
                            item = item,
                            onPedidoClick = { orderId ->
                                val intent = Intent(context, AdminDetallePedidoActivity::class.java).apply {
                                    putExtra(AdminDetallePedidoActivity.EXTRA_ORDER_ID, orderId)
                                }
                                context.startActivity(intent)
                            },
                            onCancelarClick = { orderId ->
                                cancelarPedido(orderId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPedidoRow(
    item: AdminPedidoItem,
    onPedidoClick: (Int) -> Unit,
    onCancelarClick: (Int) -> Unit
) {
    val (statusText, statusColor) = when (item.estatus) {
        1 -> "Aceptado" to Color(0xFF2563EB)
        2 -> "Pendiente" to Color(0xFFE65100)
        3 -> "En ruta" to Color(0xFF16A34A)
        4 -> "Cancelado" to Color(0xFFDC2626)
        5 -> "Retrasado" to Color(0xFFD97706)
        6 -> "Entregado" to Color(0xFF15803D)
        else -> "Estado ${item.estatus}" to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPedidoClick(item.idPedido) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("#${item.idPedido}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${item.repartidorNombre} • Cliente: ${item.nombreCliente}", fontSize = 13.sp, color = Color.DarkGray)
                Text(item.direccion, fontSize = 12.sp, color = Color.Gray)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (item.estatus != 4 && item.estatus != 6) {
                    IconButton(onClick = { onCancelarClick(item.idPedido) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar pedido", tint = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/admin/AdminDetallePedidoActivity.kt`
**Funcionalidad:** Muestra el detalle completo de un pedido individual para el administrador. Incluye la información del cliente, dirección, repartidor asignado, botones de acción para editar o cancelar el pedido, e integración con Google Maps para lanzar la ruta GPS en la app externa.

```kotlin
package mx.utng.deliverytrack.mobile.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mx.utng.deliverytrack.mobile.ui.NuevoPedidoActivity
import mx.utng.deliverytrack.shared.config.ServerConfig
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

data class AdminOrderDetail(
    val idPedido: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referencia: String,
    val descripcion: String,
    val estatus: Int,
    val repartidorNombre: String,
    val repartidorTelefono: String
)

class AdminDetallePedidoActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getIntExtra(EXTRA_ORDER_ID, -1)

        setContent {
            MaterialTheme {
                AdminDetallePedidoScreen(
                    orderId = orderId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDetallePedidoScreen(
    orderId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var orderDetail by remember { mutableStateOf<AdminOrderDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var isCanceling by remember { mutableStateOf(false) }

    val primaryBlue = Color(0xFF1A3A6B)

    fun fetchDetails() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val obj = JSONObject(text)
                    orderDetail = AdminOrderDetail(
                        idPedido = obj.getInt("id_pedido"),
                        nombreCliente = obj.getString("nombre_cliente"),
                        telefono = obj.getString("telefono"),
                        direccion = obj.getString("direccion"),
                        referencia = obj.optString("referencia_lugar", ""),
                        descripcion = obj.optString("descripcion_pedido", ""),
                        estatus = obj.getInt("estatus"),
                        repartidorNombre = if (obj.isNull("repartidor_nombre")) "Sin asignar" else obj.getString("repartidor_nombre"),
                        repartidorTelefono = obj.optString("repartidor_telefono", "")
                    )
                } else {
                    errorMessage = "Error al cargar detalle del pedido"
                }
            } catch (e: Exception) {
                errorMessage = "Error de red: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchDetails()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Pedido #$orderId", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                .background(Color(0xFFF8FAFC))
        ) {
            orderDetail?.let { item ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Tarjeta de Rastreo GPS en Mapa (Google Maps)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(14.dp)
                        ) {
                            Text("RASTREO GPS EN MAPA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val encodedAddress = java.net.URLEncoder.encode(item.direccion, "UTF-8")
                                    val webMapIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedAddress")
                                    )
                                    context.startActivity(webMapIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ver Ruta GPS en Google Maps", color = Color.White)
                            }
                        }
                    }

                    // Botones de acción: Editar y Cancelar
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(context, NuevoPedidoActivity::class.java).apply {
                                    putExtra(NuevoPedidoActivity.EXTRA_EDIT_ORDER_ID, item.idPedido)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Editar pedido", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/admin/GestionUsuariosActivity.kt`
**Funcionalidad:** Interfaz completa para la administración de usuarios del sistema (administradores y repartidores). Permite consultar la lista de usuarios, crear nuevos usuarios, editar datos/roles, y aplicar una eliminación lógica (suspensión de cuenta) a través de un diálogo de confirmación `AlertDialog`.

```kotlin
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

    // Eliminación lógica de usuario
    fun deleteUsuarioLogico(user: UserItem) {
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/usuarios/${user.idUser}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "DELETE"

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
                title = { Text("Gestión de Usuarios", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp) },
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
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

            // Diálogo modal de formulario para crear/editar usuario
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
        }
    }
}

@Composable
fun UserRowCard(
    user: UserItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(user.nombreCompleto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Tel: ${user.telefono} • Rol: ${if (user.rol == 1) "Admin" else "Repartidor"}", fontSize = 12.sp, color = Color.Gray)
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color(0xFF2563EB))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFEF4444))
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
    var rolSeleccionado by remember { mutableStateOf(userToEdit?.rol ?: 2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Editar Usuario" else "Nuevo Usuario", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre completo") })
                OutlinedTextField(value = telefono, onValueChange = { telefono = it }, label = { Text("Teléfono") })
                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    thread {
                        try {
                            val body = JSONObject().apply {
                                put("nombre_completo", nombre.trim())
                                put("telefono", telefono.trim())
                                if (contrasena.isNotBlank()) put("contrasena", contrasena)
                                put("rol", rolSeleccionado)
                                put("estatus", userToEdit?.estatus ?: 1)
                            }.toString()

                            val urlString = if (isEditMode) "${ServerConfig.BASE_URL}/api/usuarios/${userToEdit?.idUser}" else "${ServerConfig.BASE_URL}/api/usuarios"
                            val url = java.net.URL(urlString)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            applySslBypass(conn)
                            conn.requestMethod = if (isEditMode) "PUT" else "POST"
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.doOutput = true
                            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                            if (conn.responseCode == 200 || conn.responseCode == 201) {
                                onSuccess()
                            }
                        } catch (_: Exception) {}
                    }
                }
            ) {
                Text(if (isEditMode) "Editar" else "Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
```

---

## 10. Carpeta UI - Módulo de Repartidor (`ui/repartidor`)

Esta carpeta contiene la interfaz utilizada por los repartidores de flotilla para ver sus entregas, actualizar los estados de los pedidos y gestionar los datos de su perfil.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/repartidor/MisPedidosRepartidorScreen.kt`
**Funcionalidad:** Vista principal para repartidores. Posee una barra de navegación inferior con 2 pestañas ("Mis Entregas" y "Perfil"). Permite filtrar los pedidos asignados por estado, ver tarjetas detalladas de cada entrega y editar la información de la cuenta personal (nombre, teléfono y contraseña).

```kotlin
package mx.utng.deliverytrack.mobile.ui.repartidor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
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

data class PedidoCard(
    val idPedido: Int,
    val nombreCliente: String,
    val direccion: String,
    val estatus: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisPedidosRepartidorScreen(
    userSession: UserSession,
    onVerDetalleClick: (Int) -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTab by remember { mutableIntStateOf(0) } // 0: Mis Entregas, 1: Perfil
    var pedidos by remember { mutableStateOf<List<PedidoCard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedEstatusFilter by remember { mutableStateOf<Int?>(null) }

    // Estados para la pestaña de Perfil
    var profileNombre by remember { mutableStateOf(userSession.nombreCompleto) }
    var profileTelefono by remember { mutableStateOf(userSession.telefono) }

    val primaryBlue = Color(0xFF1A3A6B)
    val accentBlue = Color(0xFF2563EB)

    fun fetchRepartidorPedidos() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/repartidor/${userSession.idUser}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        PedidoCard(
                            idPedido = obj.getInt("id_pedido"),
                            nombreCliente = obj.getString("nombre_cliente"),
                            direccion = obj.getString("direccion"),
                            estatus = obj.getInt("estatus")
                        )
                    }
                    pedidos = list
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchRepartidorPedidos()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredPedidos = remember(pedidos, selectedEstatusFilter) {
        if (selectedEstatusFilter == null) pedidos else pedidos.filter { it.estatus == selectedEstatusFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (currentTab == 0) "Mis Entregas" else "Mi Perfil", fontWeight = FontWeight.Bold, color = Color.White)
                },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Mis Entregas") },
                    label = { Text("Mis Entregas") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Perfil") },
                    label = { Text("Perfil") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF1F5F9))
        ) {
            if (currentTab == 0) {
                // Lista de pedidos del repartidor
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredPedidos) { item ->
                        PedidoItemCard(item = item, onClick = { onVerDetalleClick(item.idPedido) })
                    }
                }
            } else {
                // Pestaña de Perfil
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("INFORMACIÓN DEL PERFIL", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = profileNombre,
                        onValueChange = { profileNombre = it },
                        label = { Text("Nombre Completo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileTelefono,
                        onValueChange = { profileTelefono = it },
                        label = { Text("Teléfono") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun PedidoItemCard(item: PedidoCard, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Pedido #${item.idPedido}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(item.nombreCliente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(item.direccion, fontSize = 13.sp, color = Color.Gray)
        }
    }
}
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/repartidor/DetallePedidoRepartidorActivity.kt`
**Funcionalidad:** Vista detallada de pedido para el repartidor. Le permite avanzar el flujo de trabajo de la entrega a través de peticiones HTTP PATCH al servidor: Aceptar Pedido (estatus 1), Iniciar Ruta/En camino (estatus 3), Marcar como Entregado (estatus 6) o Rechazar Pedido (estatus 4). Además, lanza la navegación GPS directa en Google Maps (`google.navigation:q=direccion`).

```kotlin
package mx.utng.deliverytrack.mobile.ui.repartidor

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mx.utng.deliverytrack.shared.config.ServerConfig
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

data class RepartidorOrderDetail(
    val idPedido: Int,
    val nombreCliente: String,
    val telefono: String,
    val direccion: String,
    val referencia: String,
    val descripcion: String,
    val estatus: Int,
    val repartidorId: Int
)

class DetallePedidoRepartidorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_COURIER_ID = "extra_courier_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getIntExtra(EXTRA_ORDER_ID, -1)
        val courierId = intent.getIntExtra(EXTRA_COURIER_ID, -1)

        setContent {
            MaterialTheme {
                DetallePedidoRepartidorScreen(
                    orderId = orderId,
                    courierId = courierId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetallePedidoRepartidorScreen(
    orderId: Int,
    courierId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var orderDetail by remember { mutableStateOf<RepartidorOrderDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUpdatingStatus by remember { mutableStateOf(false) }

    val primaryBlue = Color(0xFF1A3A6B)

    fun fetchDetails() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val obj = JSONObject(text)
                    orderDetail = RepartidorOrderDetail(
                        idPedido = obj.getInt("id_pedido"),
                        nombreCliente = obj.getString("nombre_cliente"),
                        telefono = obj.getString("telefono"),
                        direccion = obj.getString("direccion"),
                        referencia = obj.optString("referencia_lugar", ""),
                        descripcion = obj.optString("descripcion_pedido", ""),
                        estatus = obj.getInt("estatus"),
                        repartidorId = obj.optInt("id_repartidor", courierId)
                    )
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    // Actualiza el estatus del pedido vía PATCH al servidor REST
    fun updateStatus(newStatus: Int) {
        isUpdatingStatus = true
        thread {
            try {
                val body = JSONObject().apply {
                    put("estatus", newStatus)
                    put("repartidorId", if (courierId > 0) courierId else 2)
                }.toString()

                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId/estatus")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                if (conn.responseCode == 200) {
                    fetchDetails()
                }
            } catch (_: Exception) {
            } finally {
                isUpdatingStatus = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchDetails()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Entrega #$orderId", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
            orderDetail?.let { item ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Botón de Inicio de Navegación GPS (Google Maps App Externa)
                    if (item.estatus == 1 || item.estatus == 3 || item.estatus == 5) {
                        Button(
                            onClick = {
                                val encodedAddress = java.net.URLEncoder.encode(item.direccion, "UTF-8")
                                val gmmIntentUri = android.net.Uri.parse("google.navigation:q=$encodedAddress")
                                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                context.startActivity(mapIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Iniciar Navegación GPS (Google Maps)", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Botones interactivos de cambio de estatus de la entrega
                    when (item.estatus) {
                        2 -> { // Estado Pendiente -> Aceptar / Rechazar
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { updateStatus(4) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                ) {
                                    Text("Rechazar", color = Color.White)
                                }
                                Button(
                                    onClick = { updateStatus(1) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                                ) {
                                    Text("Aceptar Pedido", color = Color.White)
                                }
                            }
                        }
                        1 -> { // Estado Aceptado -> Transición a "En camino"
                            Button(
                                onClick = { updateStatus(3) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("En camino", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        3, 5 -> { // Estado En camino -> Transición a "Entregado"
                            Button(
                                onClick = { updateStatus(6) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Marcar como Entregado", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
```
