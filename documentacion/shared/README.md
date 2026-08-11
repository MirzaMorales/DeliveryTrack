# Documentación Completa del Módulo Shared

El módulo `shared` es una biblioteca común de Android reutilizable entre los módulos `mobile`, `wear` y `tv`. Su propósito principal es almacenar configuraciones globales, componentes comunes de red y base de datos, y controladores compartidos para evitar la duplicación de código en la plataforma.

A continuación, se detalla el código completo y la explicación de cada uno de los archivos del módulo.

---

## 1. Configuración del Módulo (Gradle)

### `build.gradle.kts`
Este archivo configura el módulo como una biblioteca de Android (`android.library`), establece el nivel de compilación SDK a 36 y define las dependencias comunes utilizadas por el resto de módulos para peticiones HTTP (OkHttp) y mensajería (Eclipse Paho MQTT).

```kotlin
plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
}

android {
    namespace = "mx.utng.deliverytrack.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
```

---

## 2. Configuración del Servidor

### `config/ServerConfig.kt`
Almacena de forma centralizada los puntos de acceso (endpoints) del servidor backend para llamadas HTTP (REST API) y conexiones en tiempo real (WebSockets).

```kotlin
package mx.utng.deliverytrack.shared.config

/**
 * Objeto de configuración global para almacenar los endpoints de conexión con el servidor.
 */
object ServerConfig {
    /**
     * URL base de la API REST del servidor.
     * Puede apuntar a un servidor de desarrollo local o producción en la nube.
     */
    var BASE_URL: String = "https://deliverytrack-d9ut.onrender.com"

    /**
     * URL base del WebSocket del servidor para transferencia de datos en tiempo real.
     */
    var WS_URL: String = "wss://deliverytrack-d9ut.onrender.com/ws"
}
```

---

## 3. Origen de Datos Remotos

### `data/remote/RemoteDataSource.kt`
Componente común para realizar llamadas HTTP GET asíncronas utilizando la librería OkHttp. Simplifica la consulta de APIs a través de callbacks genéricos que capturan el estado de éxito y los datos o errores correspondientes.

```kotlin
package mx.utng.deliverytrack.shared.data.remote

import mx.utng.deliverytrack.shared.config.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Origen de datos remoto para interactuar con la API REST del servidor mediante peticiones HTTP.
 * 
 * @property baseUrl URL base del servidor obtenida de la configuración.
 */
class RemoteDataSource(private val baseUrl: String = ServerConfig.BASE_URL) {
    private val client = OkHttpClient()

    /**
     * Realiza una petición GET asíncrona a un endpoint específico.
     * 
     * @param endpoint Ruta del recurso que se desea consultar.
     * @param callback Callback ejecutado al terminar la petición. Retorna un booleano (éxito/fallo) y el cuerpo de la respuesta o mensaje de error.
     */
    fun get(endpoint: String, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(false, e.message)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        callback(true, it.body?.string())
                    } else {
                        callback(false, "HTTP ${it.code}")
                    }
                }
            }
        })
    }
}
```

---

## 4. Persistencia Local Compartida

### `db/DatabaseHelper.kt`
Proporciona una plantilla y métodos de inicialización comunes para bases de datos SQLite o Room, permitiendo que la persistencia estructurada local comparta estándares.

```kotlin
package mx.utng.deliverytrack.shared.db

/**
 * Clase de utilidad para inicializar y gestionar operaciones de base de datos local compartida.
 */
class DatabaseHelper {
    /**
     * Inicializa la base de datos local compartida del dispositivo (por ejemplo, SQLite o Room).
     */
    fun initDb() {
        // Lógica de inicialización de la base de datos compartida
    }
}
```

---

## 5. Utilidades de Mensajería MQTT

### `mqtt/MqttClientHelper.kt`
Abstrae la lógica de comunicación con brokers MQTT utilizando la biblioteca Eclipse Paho. Permite conectarse, suscribirse a temas (topics), publicar payloads y desconectarse de forma simplificada, gestionando parámetros como QoS y credenciales de usuario.

```kotlin
package mx.utng.deliverytrack.shared.mqtt

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Clase de utilidad para interactuar con un servidor de mensajería MQTT.
 * 
 * Abstrae la conexión, suscripción, publicación y desconexión utilizando la biblioteca Paho MQTT.
 * 
 * @property serverUri Dirección URI del servidor broker MQTT (por ejemplo, tcp://localhost:1883).
 * @property clientId Identificador único del cliente conectado.
 * @property username Nombre de usuario para la autenticación en el broker (opcional).
 * @property password Contraseña para la autenticación en el broker (opcional).
 */
class MqttClientHelper(
    private val serverUri: String,
    private val clientId: String,
    private val username: String? = null,
    private val password: String? = null
) {
    private var mqttClient: MqttClient? = null

    /**
     * Establece una conexión con el broker MQTT de forma asíncrona.
     * 
     * @param onConnected Callback ejecutado cuando la conexión se ha establecido correctamente.
     * @param onError Callback ejecutado si la conexión falla, retornando la excepción generada.
     */
    fun connect(onConnected: () -> Unit, onError: (Throwable) -> Unit) {
        try {
            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())
            val user = username
            val pass = password
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                if (!user.isNullOrEmpty()) {
                    userName = user
                }
                if (!pass.isNullOrEmpty()) {
                    this.password = pass.toCharArray()
                }
            }
            mqttClient?.connect(options)
            onConnected()
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Se suscribe a un tema específico del broker MQTT y define el callback para procesar los mensajes entrantes.
     * 
     * @param topic Nombre del tema/canal al que desea suscribirse.
     * @param onMessage Callback invocado cada vez que se recibe un mensaje, retornando el tema origen y el payload en texto.
     */
    fun subscribe(topic: String, onMessage: (String, String) -> Unit) {
        mqttClient?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {}

            override fun messageArrived(topicReceived: String?, message: MqttMessage?) {
                if (topicReceived != null && message != null) {
                    onMessage(topicReceived, String(message.payload))
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
        mqttClient?.subscribe(topic)
    }

    /**
     * Publica un mensaje de texto en un tema específico del broker MQTT.
     * 
     * @param topic Tema al que se enviará el mensaje.
     * @param message Cuerpo del mensaje en formato de texto.
     * @param qos Nivel de calidad del servicio (QoS) para el envío (por defecto 1).
     */
    fun publish(topic: String, message: String, qos: Int = 1) {
        val mqttMessage = MqttMessage(message.toByteArray()).apply {
            this.qos = qos
        }
        mqttClient?.publish(topic, mqttMessage)
    }

    /**
     * Cierra la conexión activa con el broker MQTT de forma segura.
     */
    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (_: Exception) {}
    }
}
```

---

## 6. Repositorio de Datos Compartido

### `repository/SharedRepository.kt`
Clase repositorio común que abstrae el consumo de endpoints compartidos entre distintos submódulos del aplicativo, como la descarga de órdenes de entrega activas asociadas a un repartidor en particular.

```kotlin
package mx.utng.deliverytrack.shared.repository

import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource

/**
 * Repositorio de datos compartido entre módulos.
 * 
 * Centraliza la adquisición de datos de pedidos y recursos remotos comunes del sistema.
 * 
 * @property remoteDataSource Origen de datos remoto para realizar peticiones HTTP.
 */
class SharedRepository(private val remoteDataSource: RemoteDataSource = RemoteDataSource()) {
    /**
     * Obtiene la lista de pedidos activos asignados a un repartidor en particular.
     * 
     * @param repartidorId Identificador único del repartidor.
     * @param callback Callback que retorna éxito/fallo y el JSON de respuesta como texto.
     */
    fun fetchActiveOrders(repartidorId: Int, callback: (Boolean, String?) -> Unit) {
        remoteDataSource.get("/api/pedidos/activo/repartidor/$repartidorId", callback)
    }
}
```
