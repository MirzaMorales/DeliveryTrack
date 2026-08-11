# Documentación Completa del Módulo Wear (WearOS)

El módulo `wear` es una aplicación de Android diseñada específicamente para dispositivos WearOS (relojes inteligentes). Su propósito principal es permitir a los repartidores gestionar las entregas asignadas (visualizar detalles, aceptar, iniciar ruta y confirmar la entrega) de forma rápida y con interacciones optimizadas para pantallas circulares pequeñas, minimizando la necesidad de utilizar el teléfono móvil durante el trayecto.

A continuación, se detalla el código completo y la explicación de cada uno de los archivos del módulo.

---

## 1. Configuración del Módulo (Gradle)

### `build.gradle.kts`
Este archivo de configuración define los plugins de Android Application y Compose, establece los SDK de compilación y destino (targetSDK 36, minSDK 30), e incluye dependencias para WearOS (como `play-services-wearable`, la suite de Compose para Wear y el soporte de Splashscreen). También declara una dependencia de compilación directa con el proyecto `:shared`.

```kotlin
plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "mx.utng.deliverytrack.wear"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "mx.utng.deliverytrack"
        minSdk = 30
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
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
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material3:material3")
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
```

---

## 2. Manifiesto de la Aplicación

### `AndroidManifest.xml`
Declara los permisos indispensables para la aplicación: acceso a Internet, prevención de suspensión de pantalla (`WAKE_LOCK`) y control del motor de vibración (`VIBRATE`) para las alertas hápticas. Además, define que el hardware obligatorio es un reloj inteligente (`android.hardware.type.watch`), habilita el tráfico en texto claro para fines de pruebas locales, carga la librería `com.google.android.wearable`, y configura la actividad principal con la temática de inicio para WearOS.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <uses-feature android:name="android.hardware.type.watch" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:networkSecurityConfig="@xml/network_security_config"
        android:theme="@android:style/Theme.DeviceDefault">
        
        <uses-library
            android:name="com.google.android.wearable"
            android:required="true" />
        <uses-library
            android:name="wear-sdk"
            android:required="false" />

        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />

        <activity
            android:name="mx.utng.deliverytrack.wear.presentation.MainActivity"
            android:exported="true"
            android:taskAffinity=""
            android:theme="@style/MainActivityTheme.Starting">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## 3. Capa de Red y MQTT

### `mqtt/WearMqttManager.kt`
Gestiona la conexión y suscripción a canales MQTT mediante la biblioteca común compartida `:shared`. Permite suscribirse reactivamente al canal específico del repartidor en HiveMQ para recibir alertas urgentes del centro de control (por ejemplo, cancelaciones o asignaciones en tiempo real).

```kotlin
package mx.utng.deliverytrack.wear.mqtt

import mx.utng.deliverytrack.shared.mqtt.MqttClientHelper

/**
 * Gestor de conexión MQTT para el módulo WearOS.
 *
 * Esta clase se encarga de abrir una conexión persistente a un broker MQTT
 * y suscribir al repartidor a su canal específico de notificaciones para recibir
 * alertas hápticas y actualizaciones críticas desde la central lograda mediante HiveMQ.
 *
 * @property serverUri Dirección del servidor broker MQTT (soporta conexiones seguras ssl://).
 * @property username Nombre de usuario para la autenticación en el broker.
 * @property password Contraseña para la autenticación en el broker.
 */
class WearMqttManager(
    private val serverUri: String = "ssl://79a94522998842728c5ef7bf42fd3c30.s1.eu.hivemq.cloud:8883",
    private val username: String = "smarthealthmonitor",
    private val password: String = "linux123"
) {
    private var mqttClientHelper: MqttClientHelper? = null

    /**
     * Establece la conexión con el broker MQTT utilizando un ID de cliente único.
     *
     * @param clientId Identificador único para el cliente MQTT en esta sesión.
     * @param onConnected Función callback que se ejecuta al establecerse la conexión con éxito.
     */
    fun connect(clientId: String, onConnected: () -> Unit) {
        mqttClientHelper = MqttClientHelper(serverUri, clientId, username, password)
        mqttClientHelper?.connect(onConnected) { _ -> }
    }

    /**
     * Suscribe al cliente al canal específico de alertas del repartidor actual.
     *
     * @param courierId Identificador único del repartidor.
     * @param onAlertReceived Función callback que se ejecuta cuando se recibe una alerta en formato JSON o texto.
     */
    fun subscribeToCourierTopic(courierId: Int, onAlertReceived: (String) -> Unit) {
        mqttClientHelper?.subscribe("deliverytrack/courier/$courierId/alerts") { _, message ->
            onAlertReceived(message)
        }
    }

    /**
     * Desconecta el cliente MQTT activo y libera los recursos.
     */
    fun disconnect() {
        mqttClientHelper?.disconnect()
    }
}
```

---

## 4. Capa de Comunicación (Data Layer)

### `data/WearableDataLayerHelper.kt`
Maneja la interacción mediante la Google Play Services **Wearable Data Layer API**. Este servicio se encarga de escuchar y recibir datos enviados desde el teléfono móvil (que funciona como puente de red), tales como:
- Respuestas a la consulta del pedido activo (`/pedido/activo/response`).
- Alertas hápticas e hilos de vibración personalizados (`/pedido/alerta`).
- Cambios de estado en la entrega desde el teléfono (`/pedido/status/response`).

```kotlin
package mx.utng.deliverytrack.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/**
 * Clase de soporte para la API de Google Play Services Wearable Data Layer.
 *
 * Facilita la comunicación bidireccional mediante Bluetooth/WiFi entre el reloj inteligente
 * (WearOS) y el teléfono móvil vinculado (Mobile Hub), actuando este último como puente
 * de red hacia el servidor backend central.
 *
 * @property context Contexto de la aplicación Android.
 * @property onActiveOrderResponse Callback invocado al recibir los datos de la orden activa del repartidor.
 * @property onStatusUpdateResponse Callback invocado tras enviar una actualización de estado del pedido.
 * @property onHapticAlertReceived Callback invocado al recibir una alerta háptica del teléfono (ej. nuevo pedido o cancelación).
 */
class WearableDataLayerHelper(
    private val context: Context,
    private val onActiveOrderResponse: (success: Boolean, responseJson: JSONObject?) -> Unit,
    private val onStatusUpdateResponse: (success: Boolean, responseJson: JSONObject?) -> Unit,
    private val onHapticAlertReceived: (type: String) -> Unit
) : MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WearDataHelper"
        
        // Rutas de comunicación de datos de la API Data Layer
        private const val PATH_GET_ACTIVE = "/pedido/activo/get"
        private const val PATH_RESPONSE_ACTIVE = "/pedido/activo/response"
        private const val PATH_UPDATE_STATUS = "/pedido/status/update"
        private const val PATH_RESPONSE_STATUS = "/pedido/status/response"
        private const val PATH_ALERT = "/pedido/alerta"
    }

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    /**
     * Registra esta clase como receptor de mensajes entrantes de la Wearable Data Layer API.
     */
    fun registerListener() {
        messageClient.addListener(this)
        Log.d(TAG, "Message received listener registered")
    }

    /**
     * Remueve el registro de esta clase para dejar de recibir mensajes de la Wearable Data Layer API.
     */
    fun unregisterListener() {
        messageClient.removeListener(this)
        Log.d(TAG, "Message received listener unregistered")
    }

    /**
     * Recibe y procesa los mensajes entrantes de la Wearable Data Layer API enviados por el teléfono móvil.
     * Clasifica los payloads según la ruta asignada.
     *
     * @param event Evento de mensaje recibido que contiene la ruta y el payload en bytes.
     */
    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val payload = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "Received message on path: $path, payload: $payload")

        try {
            when (path) {
                PATH_RESPONSE_ACTIVE -> {
                    val json = JSONObject(payload)
                    val success = json.optBoolean("success", false)
                    val data = json.optJSONObject("data")
                    onActiveOrderResponse(success, data)
                }
                PATH_RESPONSE_STATUS -> {
                    val json = JSONObject(payload)
                    val success = json.optBoolean("success", false)
                    val data = json.optJSONObject("data")
                    onStatusUpdateResponse(success, data)
                }
                PATH_ALERT -> {
                    onHapticAlertReceived(payload)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming wearable message", e)
        }
    }

    /**
     * Envía una solicitud al teléfono móvil para obtener el pedido activo actual asignado al repartidor.
     *
     * @param repartidorId Identificador único del repartidor.
     */
    fun requestActiveOrder(repartidorId: Int) {
        Log.d(TAG, "Requesting active order for courier: $repartidorId")
        sendToConnectedNodes(PATH_GET_ACTIVE, repartidorId.toString())
    }

    /**
     * Envía una solicitud de actualización del estado de un pedido al teléfono móvil.
     *
     * @param orderId Identificador único del pedido.
     * @param estatus Código numérico del nuevo estatus solicitado.
     * @param repartidorId Identificador único del repartidor encargado.
     */
    fun requestStatusUpdate(orderId: Int, estatus: Int, repartidorId: Int) {
        Log.d(TAG, "Requesting status update for order: $orderId to: $estatus")
        val payload = JSONObject().apply {
            put("id_pedido", orderId)
            put("estatus", estatus)
            put("repartidorId", repartidorId)
        }.toString()

        sendToConnectedNodes(PATH_UPDATE_STATUS, payload)
    }

    /**
     * Transmite un mensaje en bytes a todos los dispositivos móviles (nodos) conectados al reloj.
     *
     * @param path Ruta semántica del mensaje.
     * @param payload Datos del mensaje formateados como String.
     */
    private fun sendToConnectedNodes(path: String, payload: String) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected phone nodes found to send message")
                    return@addOnSuccessListener
                }
                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, payload.toByteArray(Charsets.UTF_8))
                        .addOnSuccessListener {
                            Log.d(TAG, "Message successfully sent to node ${node.displayName} on path $path")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send message to node ${node.displayName} on path $path", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch connected nodes", e)
            }
    }
}
```

---

## 5. Modelos de Datos y Presentación

Los datos consumidos por la aplicación se estructuran principalmente en torno a la sesión de autenticación del repartidor y el listado de pedidos activos.

### `WearCourierSession` (Definido en `auth/WearLoginScreen.kt`)
Modelo de datos que almacena el estado de sesión activa del repartidor tras autenticarse con éxito en el servidor.
```kotlin
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
```

### `WearPedidoCardItem` (Definido en `pedidos/WearPedidosCardsScreen.kt`)
Clase de datos utilizada para mapear los elementos individuales del listado de entregas activas asignadas al repartidor.
```kotlin
/**
 * Modelo de datos que representa una entrega individual mapeada para su visualización
 * en forma de tarjeta en la pantalla del reloj inteligente.
 *
 * @property idPedido Identificador único de la entrega.
 * @property nombreCliente Nombre del cliente receptor.
 * @property direccion Ubicación o dirección de entrega.
 * @property descripcion Contenido u observaciones del pedido.
 * @property estatus Código numérico del estatus actual del pedido.
 */
data class WearPedidoCardItem(
    val idPedido: Int,
    val nombreCliente: String,
    val direccion: String,
    val descripcion: String,
    val estatus: Int
)
```

---

## 6. Componentes de Interfaz Gráfica (UI)

### `MainActivity.kt`
Este archivo contiene la lógica central del flujo de navegación del reloj. Inicia y configura el `WearableDataLayerHelper` para capturar eventos de prueba o alertas hápticas de cancelación. Coordina las transiciones entre las pantallas de Inicio de Sesión, Listado de Entregas y Detalle de Pedido, e implementa la actualización directa del estado del pedido en el backend mediante HTTP con soporte SSL Bypass.

```kotlin
package mx.utng.deliverytrack.wear.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import mx.utng.deliverytrack.shared.config.ServerConfig
import mx.utng.deliverytrack.wear.data.WearableDataLayerHelper
import mx.utng.deliverytrack.wear.presentation.auth.WearCourierSession
import mx.utng.deliverytrack.wear.presentation.auth.WearLoginScreen
import mx.utng.deliverytrack.wear.presentation.pedidos.WearPedidoCardItem
import mx.utng.deliverytrack.wear.presentation.pedidos.WearPedidosCardsScreen
import mx.utng.deliverytrack.wear.presentation.theme.DeliveryTrackTheme
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Actividad principal del módulo Wear (WearOS).
 *
 * Se encarga de la inicialización de la Wearable Data Layer API, coordinar las pantallas
 * de flujo de usuario (Login, Listado de Pedidos Activos, Detalles del Pedido) y gestionar la
 * actualización de estados a través de peticiones HTTP directas al backend.
 */
class MainActivity : ComponentActivity() {

    private lateinit var dataLayerHelper: WearableDataLayerHelper

    // Estados reactivos que controlan la sesión y navegación del reloj
    private var activeCourier by mutableStateOf<WearCourierSession?>(null)
    private var selectedPedido by mutableStateOf<WearPedidoCardItem?>(null)

    // Estados reactivos que representan los datos del pedido en pantalla
    private var activeOrderId by mutableStateOf<Int?>(null)
    private var clientName by mutableStateOf("")
    private var addressText by mutableStateOf("")
    private var orderDescription by mutableStateOf("")
    private var orderStatus by mutableStateOf<Int?>(null)

    private var isLoading by mutableStateOf(false)
    private var statusMessage by mutableStateOf("")

    companion object {
        private const val TAG = "WearMainActivity"
    }

    /**
     * Inicializa la actividad y la lógica de escucha del WearableDataLayerHelper.
     * Define la interfaz gráfica en Compose bajo el tema general.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se inicializa el puente de comunicación local con el dispositivo celular
        dataLayerHelper = WearableDataLayerHelper(
            context = this,
            onActiveOrderResponse = { success, data ->
                Log.d(TAG, "Active order response callback. Success=$success")
                if (success && data != null) {
                    val id = data.optInt("id_pedido", -1)
                    activeOrderId = id
                    clientName = data.optString("nombre_cliente", "")
                    val dir = data.optString("direccion", "")
                    val ref = data.optString("referencia_lugar", "")
                    addressText = if (ref.isNotEmpty()) "$dir, $ref" else dir
                    orderDescription = data.optString("descripcion_pedido", "")

                    val oldStatus = orderStatus
                    val newStatus = data.optInt("estatus", -1)
                    orderStatus = newStatus
                    statusMessage = ""

                    // Si llega un pedido con estatus 'Pendiente' (2), se genera alerta háptica
                    if (oldStatus == null && newStatus == 2) {
                        triggerHapticAlert("nuevo")
                    }
                } else {
                    resetOrderState()
                    statusMessage = ""
                }
            },
            onStatusUpdateResponse = { _, _ -> },
            onHapticAlertReceived = { type ->
                Log.d(TAG, "Haptic alert notification received: $type")
                triggerHapticAlert(type)

                if (type.lowercase() == "cancelado") {
                    statusMessage = "Pedido cancelado por admin"
                }
            }
        )

        setContent {
            DeliveryTrackTheme {
                AppScaffold {
                    val courier = activeCourier
                    val order = selectedPedido

                    // Enrutamiento local de pantallas mediante variables de estado
                    if (courier == null) {
                        WearLoginScreen(
                            onLoginSuccess = { session ->
                                activeCourier = session
                            }
                        )
                    } else if (order == null) {
                        WearPedidosCardsScreen(
                            courierId = courier.idUser,
                            courierName = courier.nombreCompleto,
                            onPedidoCardClick = { cardItem ->
                                selectedPedido = cardItem
                                activeOrderId = cardItem.idPedido
                                clientName = cardItem.nombreCliente
                                addressText = cardItem.direccion
                                orderDescription = cardItem.descripcion
                                orderStatus = cardItem.estatus
                                statusMessage = ""
                            },
                            onChangeCourierClick = {
                                activeCourier = null
                                selectedPedido = null
                            }
                        )
                    } else {
                        WearOrderDetailScreen(
                            activeOrderId = activeOrderId,
                            clientName = clientName,
                            addressText = addressText,
                            orderDescription = orderDescription,
                            orderStatus = orderStatus,
                            isLoading = isLoading,
                            statusMessage = statusMessage,
                            onBackToCardsList = {
                                selectedPedido = null
                            },
                            onAccept = { updateOrderStatusDirect(newStatus = 1) },
                            onReject = { updateOrderStatusDirect(newStatus = 4) },
                            onEnCamino = { updateOrderStatusDirect(newStatus = 3) },
                            onEntregado = { updateOrderStatusDirect(newStatus = 6) }
                        )
                    }
                }
            }
        }
    }

    /**
     * Envía la actualización de estado del pedido directamente a la API REST del backend
     * mediante HTTP PUT en un hilo de ejecución secundario para garantizar su recepción.
     *
     * @param newStatus Código numérico del nuevo estatus solicitado (ej: 1 = Aceptado, 3 = En camino, 6 = Entregado).
     */
    private fun updateOrderStatusDirect(newStatus: Int) {
        val id = activeOrderId ?: return
        isLoading = true
        statusMessage = ""

        thread {
            try {
                val body = JSONObject().apply {
                    put("estatus", newStatus)
                }.toString()

                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$id")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypassWear(conn)
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                val code = conn.responseCode

                runOnUiThread {
                    isLoading = false
                    if (code == 200) {
                        orderStatus = newStatus
                        statusMessage = when (newStatus) {
                            6 -> "¡Entrega completada!"
                            4 -> "Pedido cancelado"
                            else -> ""
                        }
                    } else {
                        val errText = try {
                            conn.errorStream?.bufferedReader()?.readText()
                        } catch (_: Exception) { null }
                        Log.e(TAG, "Status update failed. Code=$code Body=$errText")
                        statusMessage = "Error al actualizar (código $code)"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating order status directly", e)
                runOnUiThread {
                    isLoading = false
                    statusMessage = "Error de red: ${e.message}"
                }
            }
        }
    }

    /**
     * Limpia los estados locales de la entrega activa cargada en pantalla.
     */
    private fun resetOrderState() {
        activeOrderId = null
        clientName = ""
        addressText = ""
        orderDescription = ""
        orderStatus = null
    }

    /**
     * Genera un patrón de vibración (alerta háptica) en el reloj del repartidor
     * según el tipo de notificación recibida.
     *
     * @param type Tipo de alerta a disparar ("nuevo" para nuevo pedido, o "cancelado").
     */
    private fun triggerHapticAlert(type: String) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = when (type.lowercase()) {
            "nuevo" -> longArrayOf(0, 150, 100, 150)
            "cancelado" -> longArrayOf(0, 300, 100, 100, 100, 100)
            else -> longArrayOf(0, 200)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /**
     * Registra el callback de escucha de mensajes al iniciar el flujo de la aplicación.
     */
    override fun onStart() {
        super.onStart()
        dataLayerHelper.registerListener()
    }

    /**
     * Remueve el callback de escucha de mensajes al pausar o cerrar la aplicación.
     */
    override fun onStop() {
        super.onStop()
        dataLayerHelper.unregisterListener()
    }
}

/**
 * Función auxiliar para omitir las validaciones de cadenas SSL/TLS para pruebas locales HTTPS en la MainActivity.
 *
 * @param conn Instancia de conexión HTTP a la que se le aplicará el bypass.
 */
private fun applySslBypassWear(conn: java.net.HttpURLConnection) {
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
 * Composable que renderiza la pantalla de detalle de un pedido seleccionado en el WearOS.
 *
 * Muestra información clave del pedido (cliente, dirección, notas) y habilita un botón interactivo
 * contextual de acuerdo al estatus de la entrega, permitiendo cambiar el estatus mediante clics.
 *
 * @param activeOrderId Identificador único del pedido actual.
 * @param clientName Nombre del cliente receptor.
 * @param addressText Dirección de entrega con referencias.
 * @param orderDescription Observaciones del pedido.
 * @param orderStatus Código numérico del estatus del pedido.
 * @param isLoading Bandera de estado que desactiva botones y muestra spinner al actualizar.
 * @param statusMessage Mensaje descriptivo de éxito o error al realizar operaciones.
 * @param onBackToCardsList Callback invocado para regresar al listado general de entregas.
 * @param onAccept Callback para aceptar la entrega.
 * @param onReject Callback para rechazar la entrega.
 * @param onEnCamino Callback para marcar el pedido en camino.
 * @param onEntregado Callback para confirmar la entrega exitosa del pedido.
 */
@Composable
fun WearOrderDetailScreen(
    activeOrderId: Int?,
    clientName: String,
    addressText: String,
    orderDescription: String,
    orderStatus: Int?,
    isLoading: Boolean,
    statusMessage: String,
    onBackToCardsList: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnCamino: () -> Unit,
    onEntregado: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            // Botón de regresar
            item {
                Button(
                    onClick = onBackToCardsList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 28.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33CBD5E1))
                ) {
                    Text(
                        text = "← Regresar",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Cabecera con ID de pedido
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(
                        text = "Pedido #${activeOrderId ?: ""}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }
            }

            // Spinner de carga activa
            if (isLoading) {
                item {
                    Text(
                        text = "Cargando...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    )
                }
            }

            // Mensajes informativos de estatus
            if (statusMessage.isNotEmpty()) {
                item {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }

            // Nombre de cliente
            item {
                Text(
                    text = clientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            // Ubicación del cliente
            item {
                Text(
                    text = "📍 $addressText",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Observaciones del pedido
            if (orderDescription.isNotEmpty()) {
                item {
                    Text(
                        text = orderDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(6.dp)) }

            // Fila de botones de acción contextuales según estatus del pedido
            item {
                when (orderStatus) {
                    2 -> { // Pendiente: Aceptar o Rechazar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = onReject,
                                enabled = !isLoading,
                                modifier = Modifier.weight(0.30f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text("Rechazar", fontSize = 10.sp)
                            }
                            Button(
                                onClick = onAccept,
                                enabled = !isLoading,
                                modifier = Modifier.weight(0.30f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                            ) {
                                Text("Aceptar", fontSize = 10.sp)
                            }
                        }
                    }
                    1 -> { // Aceptado: Iniciar ruta (En camino)
                        Button(
                            onClick = onEnCamino,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text("En camino", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    3, 5 -> { // En camino o Retrasado: Entregar
                        Button(
                            onClick = onEntregado,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                        ) {
                            Text("Entregado", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    6 -> { // Entregado: Indicación de completado
                        Text(
                            text = "✓ ¡Entrega completada!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
```

### `presentation/auth/WearLoginScreen.kt`
Esta pantalla provee el inicio de sesión a través de un teclado numérico optimizado para la pantalla WearOS. El usuario ingresa su teléfono y contraseña, los cuales se validan directamente con el endpoint de login de la API REST del backend (`/api/auth/login`) omitiendo el cifrado de certificados SSL para pruebas locales (Bypass SSL).

```kotlin
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
```

### `presentation/pedidos/WearPedidosCardsScreen.kt`
Esta pantalla recupera y muestra el listado de pedidos activos asignados al repartidor. Consume directamente la API REST del backend (`/api/pedidos/repartidor/{courierId}`) filtrando los pedidos completados y cancelados, y pintándolos dentro de una estructura optimizada circular (`TransformingLazyColumn`). Ofrece la opción de seleccionar un pedido para abrir su detalle o bien de cerrar la sesión activa.

```kotlin
package mx.utng.deliverytrack.wear.presentation.pedidos

import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.concurrent.thread

/**
 * Modelo de datos que representa una entrega individual mapeada para su visualización
 * en forma de tarjeta en la pantalla del reloj inteligente.
 *
 * @property idPedido Identificador único de la entrega.
 * @property nombreCliente Nombre del cliente receptor.
 * @property direccion Ubicación o dirección de entrega.
 * @property descripcion Contenido u observaciones del pedido.
 * @property estatus Código numérico del estatus actual del pedido.
 */
data class WearPedidoCardItem(
    val idPedido: Int,
    val nombreCliente: String,
    val direccion: String,
    val descripcion: String,
    val estatus: Int
)

/**
 * Omitir la validación de la cadena de certificados SSL para llamadas HTTPS en entornos locales de desarrollo.
 *
 * @param conn Instancia de conexión HttpURLConnection a la que se aplicará el bypass de SSL.
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
 * Pantalla que muestra el listado de pedidos activos asignados al repartidor.
 *
 * Consume de manera directa los endpoints del backend (`/api/pedidos/repartidor/{courierId}`)
 * en un hilo secundario y renderiza una lista optimizada con tarjetas de pedidos activos, filtrando
 * automáticamente aquellos con estatus completado (6) o cancelado (4). Permite además realizar logout.
 *
 * @param courierId Identificador único del repartidor.
 * @param courierName Nombre completo del repartidor a desplegar en la cabecera.
 * @param onPedidoCardClick Callback invocado cuando el repartidor hace clic sobre una tarjeta de entrega activa.
 * @param onChangeCourierClick Callback invocado para cerrar la sesión activa del repartidor actual.
 */
@Composable
fun WearPedidosCardsScreen(
    courierId: Int,
    courierName: String,
    onPedidoCardClick: (WearPedidoCardItem) -> Unit,
    onChangeCourierClick: () -> Unit
) {
    var pedidos by remember { mutableStateOf<List<WearPedidoCardItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Efecto lanzado al iniciar o ante cambios en courierId o reloadTrigger para refrescar el listado
    LaunchedEffect(courierId, reloadTrigger) {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/repartidor/$courierId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map {
                        val obj = arr.getJSONObject(it)
                        WearPedidoCardItem(
                            idPedido = obj.getInt("id_pedido"),
                            nombreCliente = obj.getString("nombre_cliente"),
                            direccion = obj.getString("direccion"),
                            descripcion = obj.optString("descripcion_pedido", ""),
                            estatus = obj.getInt("estatus")
                        )
                    }
                    pedidos = list.filter { it.estatus != 6 && it.estatus != 4 }
                } else {
                    pedidos = emptyList()
                }
            } catch (_: Exception) {
                pedidos = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            // Cabecera con nombre del repartidor y cantidad de entregas
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = courierName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Mis Entregas (${pedidos.size})",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Renderizado condicional según estado de carga y disponibilidad de pedidos
            if (isLoading) {
                item {
                    Text(
                        text = "Buscando entregas...",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            } else if (pedidos.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sin entregas activas...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "esperando asignaciones...",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(pedidos.size) { index ->
                    val item = pedidos[index]
                    val statusText = when (item.estatus) {
                        1 -> "Aceptado"
                        2 -> "Pendiente"
                        3 -> "En camino"
                        4 -> "Cancelado"
                        5 -> "Retrasado"
                        6 -> "Entregado"
                        else -> "Estatus ${item.estatus}"
                    }

                    Card(
                        onClick = { onPedidoCardClick(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Pedido #${item.idPedido} • $statusText",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.nombreCliente,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                            Text(
                                text = "📍 ${item.direccion}",
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
            
            // Botón para cambiar de usuario (Cerrar sesión)
            item {
                Button(
                    onClick = onChangeCourierClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 28.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33CBD5E1))
                ) {
                    Text(
                        text = "Cerrar sesión",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
```

### `presentation/theme/Theme.kt`
Define la temática y paleta de colores del diseño de interfaces Compose Material 3 Wearable en el dispositivo reloj inteligente.

```kotlin
package mx.utng.deliverytrack.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Tema principal de diseño para la aplicación DeliveryTrack WearOS.
 *
 * Configura la paleta de colores, tipografías y formas de Compose Material 3
 * para adaptarlos al hardware de relojes inteligentes (WearOS).
 *
 * @param content El árbol de componentes Composable que se renderizará bajo esta temática.
 */
@Composable
fun DeliveryTrackTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}
```
