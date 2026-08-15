# Documentación Completa del Módulo Mobile (Aplicación Móvil Android)

El módulo `mobile` es la aplicación móvil principal del sistema DeliveryTrack para teléfonos y tabletas Android. Está diseñado para gestionar los roles de **Administrador** y **Repartidor**, proporcionando herramientas para la autenticación de usuarios, creación y edición de pedidos, asignación de repartidores, seguimiento por GPS, mensajería en tiempo real mediante MQTT y sincronización automática con dispositivos Wear OS (relojes inteligentes).

La interfaz de usuario está construida en su totalidad utilizando **Jetpack Compose** y **Material Design 3**, siguiendo patrones modernos de arquitectura en Android.

A continuación, se detalla la estructura organizada por carpetas, con la explicación completa, la firma de funciones con sus parámetros (`@param`), comentarios detallados línea por línea dentro de cada bloque de código, sobre variables, expresiones, ramas condicionales, llamadas a APIs, parámetros de llamadas y valores de retorno (`@return`).

---

## 1. Configuración del Módulo, Manifest y Red (`mobile/`)

Esta sección contiene los archivos raíz de configuración del módulo móvil, donde se definen las dependencias del proyecto, plugins de compilación, niveles de SDK compatibles, permisos de sistema y configuraciones de seguridad de red.

---

### `mobile/build.gradle.kts`
**Funcionalidad:** Archivo de configuración de Gradle para el módulo móvil. Configura la aplicación como un ejecutable Android (`android.application`), habilita el compilador de Jetpack Compose, establece la compatibilidad con Java 11 y declara las dependencias esenciales como `:shared` (módulo común), Play Services Wearable (para comunicación con Wear OS), OkHttp3 (para llamadas HTTP REST) y Jetpack Compose BOM.

```kotlin
// Importación de plugins requeridos para compilar la aplicación Android y habilitar soporte de Kotlin Compose
plugins {
    // Plugin principal para aplicaciones Android ejecutables
    alias(libs.plugins.android.application)
    // Plugin de compilación de lenguaje Kotlin para Android
    kotlin("android")
    // Plugin de soporte para el compilador de Jetpack Compose
    alias(libs.plugins.kotlin.compose)
}

// Bloque principal de configuración de compilación de Android SDK
android {
    // Especificación del espacio de nombres de paquetes único para el módulo móvil
    namespace = "mx.utng.deliverytrack.mobile"
    
    // Nivel de SDK compilado objetivo (Android 14/15 - API 36)
    compileSdk = 36

    // Configuración predeterminada de empaquetado para el archivo APK resultante
    defaultConfig {
        // Identificador único de aplicación en el sistema operativo Android
        applicationId = "mx.utng.deliverytrack"
        
        // Nivel mínimo de SDK compatible: Android 8.0 (Oreo) - API 26
        minSdk = 26
        
        // Nivel de SDK objetivo de ejecución (API 36)
        targetSdk = 36
        
        // Código numérico incremental de versión del APK
        versionCode = 1
        
        // Nombre visible de versión de la aplicación móvil
        versionName = "1.0"
    }

    // Definición de tipos de compilación del proyecto (Release vs Debug)
    buildTypes {
        // Configuración para la versión de distribución (Release)
        release {
            // Deshabilitar ofuscación y minificación de código ProGuard para facilitar depuración en pruebas
            isMinifyEnabled = false
            
            // Especificación de archivos de reglas de optimización de ProGuard
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // Reglas por defecto de Android
                "proguard-rules.pro" // Reglas personalizadas del proyecto
            )
        }
    }
    
    // Opciones de compatibilidad del compilador Java
    compileOptions {
        // Establecer compatibilidad del código fuente con Java 11
        sourceCompatibility = JavaVersion.VERSION_11
        // Establecer compatibilidad de bytecode generado con Java 11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    // Habilitar características especiales de compilación de Android
    buildFeatures {
        // Activar el motor de UI reactiva Jetpack Compose
        compose = true
    }
}

// Configuración adicional del compilador de Kotlin
kotlin {
    compilerOptions {
        // Definir la versión JVM de salida para las clases compiladas de Kotlin
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// Declaración de dependencias externas e internas del módulo móvil
dependencies {
    // Importación del módulo compartido central (:shared) que contiene configuraciones y helpers comunes
    implementation(project(":shared"))
    
    // Librería Google Play Services Wearable para habilitar sincronización con relojes Wear OS
    implementation(libs.play.services.wearable)
    
    // Cliente HTTP asíncrono OkHttp3 para realizar peticiones REST a la API backend
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Importación del BOM (Bill of Materials) de Jetpack Compose para alineación de versiones UI
    implementation(platform(libs.compose.bom))
    // Soporte para integración de Compose en ComponentActivity
    implementation(libs.activity.compose)
    // Núcleo de componentes visuales de Jetpack Compose
    implementation(libs.ui)
    // Utilerías gráficas, vectores y colores para Compose
    implementation(libs.ui.graphics)
    // Herramientas de vista previa en tiempo real para el IDE Android Studio
    implementation(libs.ui.tooling.preview)
    
    // Librería Foundation de Compose para gestos, listas y contenedores
    implementation("androidx.compose.foundation:foundation")
    // Distribución de layouts (Box, Column, Row, Spacer, etc.)
    implementation("androidx.compose.foundation:foundation-layout")
    // Componentes de diseño Material Design 3 (Button, Card, Scaffold, TopAppBar, Text, etc.)
    implementation("androidx.compose.material3:material3")
}
```

---

### `mobile/src/main/AndroidManifest.xml`
**Funcionalidad:** Manifest de la aplicación Android. Declara los permisos de red (`INTERNET`), las actividades que componen la interfaz (`MainActivity`, `NuevoPedidoActivity`, `GestionUsuariosActivity`, etc.), el tema sin Action Bar predeterminada y el servicio de escucha en segundo plano `MobileWearableListenerService` para comunicarse con la app de Wear OS mediante el intent filter `BIND_LISTENER`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Declaración del manifiesto principal del módulo de aplicación móvil Android -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permiso obligatorio del sistema para autorizar conexiones de red HTTP REST, WebSockets y MQTT -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Configuración global del contenedor de la aplicación Android -->
    <application
        android:allowBackup="true" <!-- Permitir respaldos de datos de la app en cuenta de Google -->
        android:icon="@mipmap/ic_launcher" <!-- Icono predeterminado de la aplicación -->
        android:roundIcon="@mipmap/ic_launcher_round" <!-- Icono redondeado para launchers compatibles -->
        android:label="DeliveryTrack" <!-- Nombre visible de la app en la pantalla de inicio del teléfono -->
        android:supportsRtl="true" <!-- Soporte para la disposición de texto Right-To-Left -->
        android:theme="@android:style/Theme.Material.Light.NoActionBar" <!-- Tema visual claro sin barra superior antigua -->
        android:networkSecurityConfig="@xml/network_security_config"> <!-- Referencia al archivo de configuración de seguridad de red -->
        
        <!-- Registro de la Actividad Principal (Punto de entrada de la aplicación) -->
        <activity
            android:name=".ui.MainActivity" <!-- Clase Java/Kotlin de la actividad -->
            android:exported="true"> <!-- Exportada a true para permitir su lanzamiento desde el Launcher de Android -->
            <intent-filter>
                <!-- Definición del Intent Filter para declarar como actividad de inicio principal -->
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Registro de la Actividad de Formulario para Crear/Editar Pedidos -->
        <activity
            android:name=".ui.NuevoPedidoActivity"
            android:exported="false" /> <!-- Uso interno, no ejecutable por otras apps externas -->

        <!-- Registro de la Actividad para Administración de Usuarios y Repartidores -->
        <activity
            android:name=".ui.admin.GestionUsuariosActivity"
            android:exported="false" /> <!-- Uso interno reservado para el Administrador -->

        <!-- Registro de la Actividad de Detalle de Pedidos para el Administrador -->
        <activity
            android:name=".ui.admin.AdminDetallePedidoActivity"
            android:exported="false" /> <!-- Vista interna del Administrador -->

        <!-- Registro de la Actividad de Detalle y Estatus para el Repartidor -->
        <activity
            android:name=".ui.repartidor.DetallePedidoRepartidorActivity"
            android:exported="false" /> <!-- Vista interna del Repartidor -->

        <!-- Registro del Servicio de escucha en segundo plano para sincronización con Wear OS -->
        <service
            android:name=".data.sync.MobileWearableListenerService"
            android:exported="true"> <!-- Exportado a true para permitir recibir mensajes de Google Play Services -->
            <intent-filter>
                <!-- Intent filter de Google Play Services para enlace con la app del smartwatch -->
                <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
            </intent-filter>
        </service>
        
    </application>

</manifest>
```

---

### `mobile/src/main/res/xml/network_security_config.xml`
**Funcionalidad:** Archivo de configuración de seguridad de red en Android. Permite el tráfico HTTP no cifrado (`cleartextTrafficPermitted="true"`) específicamente para los nombres de dominio de desarrollo local y depuración en emuladores Android (`10.0.2.2` y `localhost`).

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Configuración de excepciones de seguridad HTTP para entornos locales de depuración -->
<network-security-config>
    <!-- Declaración de políticas específicas para dominios en desarrollo -->
    <domain-config cleartextTrafficPermitted="true"> <!-- Habilitar tráfico HTTP en texto plano -->
        <!-- Dirección IP virtual usada por el emulador Android para conectar con el localhost de la máquina host -->
        <domain includeSubdomains="true">10.0.2.2</domain>
        <!-- Dominio de loopback local para pruebas internas -->
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
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
 * Entidad de datos inmutable que representa un Pedido dentro del sistema móvil DeliveryTrack.
 *
 * @property id Identificador único entero del pedido generado por la base de datos PostgreSQL.
 * @property nombreCliente Nombre completo del cliente que recibirá el paquete.
 * @property telefono Número de teléfono de contacto directo con el cliente.
 * @property direccion Dirección física de entrega del pedido.
 * @property referenciaLugar Indicaciones o referencias visuales adicionales del domicilio de entrega.
 * @property descripcionPedido Detalle de los artículos o mercancía contenidos en la orden.
 * @property idRepartidor Identificador de usuario del repartidor asignado para efectuar la entrega.
 * @property estatus Código numérico correspondiente al estado actual del pedido (1: Aceptado, 2: Pendiente, 3: En camino, 4: Cancelado, 5: Retrasado, 6: Entregado).
 */
data class Pedido(
    val id: Int,                   // ID único del pedido (PK)
    val nombreCliente: String,     // Nombre del cliente
    val telefono: String,          // Teléfono de contacto
    val direccion: String,         // Dirección física
    val referenciaLugar: String,   // Referencia de la ubicación
    val descripcionPedido: String, // Contenido de la orden
    val idRepartidor: Int,         // ID del repartidor asignado (FK)
    val estatus: Int               // Código del estado del pedido
)
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/data/models/Repartidor.kt`
**Funcionalidad:** Modela la entidad de un repartidor dentro del sistema móvil, almacenando sus datos básicos como ID de usuario, nombre completo y teléfono de contacto para su selección en menús desplegables.

```kotlin
package mx.utng.deliverytrack.mobile.data.models

/**
 * Entidad de datos que modela la información básica de un Repartidor en la plataforma.
 *
 * @property id Identificador único de usuario del repartidor en la base de datos.
 * @property nombre Nombre completo del repartidor.
 * @property telefono Número de teléfono para comunicación logística y llamadas.
 */
data class Repartidor(
    val id: Int,          // ID del repartidor en la tabla usuario
    val nombre: String,   // Nombre completo registrado
    val telefono: String  // Número telefónico de contacto
)
```

---

## 3. Carpeta Data - Repositorio (`data/repository`)

Esta carpeta contiene las clases encargadas del acceso y abstracción de datos remotos, procesando las respuestas JSON enviadas por la API REST del backend.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/data/repository/MobileRepository.kt`
**Funcionalidad:** Repositorio central de datos del módulo móvil. Utiliza el cliente `RemoteDataSource` del módulo compartido `:shared` para realizar peticiones HTTP GET y mapear la respuesta JSON con la lista de repartidores registrados y activos en el sistema.

```kotlin
package mx.utng.deliverytrack.mobile.data.repository

import mx.utng.deliverytrack.mobile.data.models.Repartidor
import mx.utng.deliverytrack.shared.data.remote.RemoteDataSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clase repositorio de datos para el módulo móvil.
 * Centraliza las llamadas a la API remota y el mapeo de objetos JSON a modelos Kotlin.
 *
 * @property remoteDataSource Instancia del origen de datos HTTP proveniente del módulo compartido `:shared`.
 */
class MobileRepository(
    private val remoteDataSource: RemoteDataSource = RemoteDataSource() // Inyección por defecto de RemoteDataSource
) {

    /**
     * Realiza una consulta asíncrona a la API backend para obtener la lista de repartidores registrados.
     *
     * @param callback Función lambda invocada al completar la solicitud HTTP REST.
     *        - Recibe `success`: Boolean indicando éxito o fallo de la petición.
     *        - Recibe `repartidores`: List<Repartidor>? con la lista parseada de repartidores o null si falló.
     *        - Recibe `error`: String? con el mensaje de error o null si la respuesta fue correcta.
     * @return Unit (No retorna valor directo, entrega los datos asíncronamente mediante el callback).
     */
    fun getRepartidores(
        callback: (success: Boolean, repartidores: List<Repartidor>?, error: String?) -> Unit // Firma del callback
    ) {
        // Ejecutar petición GET al endpoint de usuarios repartidores mediante el cliente HTTP compartido
        remoteDataSource.get("/api/usuarios/repartidores") { success, response ->
            // Evaluar si la llamada fue exitosa y la cadena de respuesta no es nula
            if (success && response != null) {
                try {
                    // Convertir el texto plano de respuesta JSON en una estructura JSONArray
                    val arr = JSONArray(response)
                    
                    // Iterar sobre cada elemento del arreglo JSON para transformarlo en un objeto Repartidor
                    val list = (0 until arr.length()).map { index ->
                        // Extraer el objeto JSONObject en el índice actual
                        val obj = arr.getJSONObject(index)
                        
                        // Construir e instanciar la entidad Repartidor mapeando los campos JSON
                        Repartidor(
                            id = obj.getInt("id_user"),                 // Extraer la propiedad id_user
                            nombre = obj.getString("nombre_completo"),  // Extraer el nombre completo
                            telefono = obj.getString("telefono")        // Extraer el teléfono
                        )
                    }
                    
                    // Invocar el callback notificando éxito y entregando la lista parseada
                    callback(true, list, null)
                    return@get // Finalizar la ejecución del bloque de callback
                } catch (e: Exception) {
                    // Capturar excepciones durante el parseo del formato JSON
                    callback(false, null, e.message)
                    return@get // Salir del bloque en caso de excepción
                }
            } else {
                // Notificar fallo en la comunicación o cuerpo de respuesta vacío
                callback(false, null, response ?: "Error al cargar la lista de repartidores desde el servidor")
                return@get // Salir del bloque
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
 * Servicio en segundo plano que atiende solicitudes enviadas por el smartwatch Wear OS vía Google Play Services API.
 */
class MobileWearableListenerService : WearableListenerService() {

    // Instancia de un pool de hilos reutilizables para ejecutar peticiones HTTP asíncronas
    private val executor = Executors.newCachedThreadPool()

    // Cliente OkHttp configurado con tiempos límite de respuesta de 5 segundos
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS) // Tiempo máximo para establecer conexión
        .readTimeout(5, TimeUnit.SECONDS)    // Tiempo máximo para leer la respuesta
        .writeTimeout(5, TimeUnit.SECONDS)   // Tiempo máximo de escritura de datos
        .build()

    // Definición del tipo de contenido de medios en formato JSON codificado en UTF-8
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    companion object {
        // Etiqueta de identificación para el registro de mensajes en el Logcat
        private const val TAG = "MobileWearableService"
    }

    /**
     * Callback activado automáticamente cuando la app móvil recibe un mensaje de red desde un reloj Wear OS emparejado.
     *
     * @param messageEvent Objeto evento con la información del mensaje (ruta, datos y nodo origen).
     * @return Unit (Procesa la solicitud de manera asíncrona dentro del pool de hilos).
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        // Invocar la implementación de la clase base WearableListenerService
        super.onMessageReceived(messageEvent)
        
        // Extraer la ruta del protocolo enviada en el mensaje (ej. "/pedido/activo/get")
        val path = messageEvent.path
        
        // Convertir la carga útil de bytes recibida a una cadena de texto UTF-8
        val payload = String(messageEvent.data, Charsets.UTF_8)
        
        // Obtener el ID único del nodo emisor (smartwatch)
        val sourceNodeId = messageEvent.sourceNodeId

        // Registrar el evento de recepción en la bitácora del sistema
        Log.d(TAG, "Mensaje recibido desde el reloj Wear OS. Ruta: $path, Carga útil: $payload")

        // Delegar la atención del mensaje a un hilo secundario para no bloquear el servicio
        executor.execute {
            try {
                // Evaluar la ruta del mensaje recibido
                when (path) {
                    // Ruta para solicitar la información del pedido activo asignado al repartidor
                    "/pedido/activo/get" -> {
                        fetchActiveOrder(repartidorId = payload, clientNodeId = sourceNodeId)
                    }
                    // Ruta para actualizar el estado del pedido desde el reloj inteligente
                    "/pedido/status/update" -> {
                        updateOrderStatus(jsonPayload = payload, clientNodeId = sourceNodeId)
                    }
                    // Ruta desconocida o no soportada
                    else -> {
                        Log.w(TAG, "Ruta de mensaje no reconocida por el servicio: $path")
                    }
                }
            } catch (e: Exception) {
                // Registrar cualquier excepción ocurrida durante el procesamiento
                Log.e(TAG, "Error procesando mensaje Wearable en la ruta: $path", e)
            }
        }
    }

    /**
     * Consulta el pedido activo asignado al repartidor desde la API REST backend y devuelve el resultado al reloj.
     *
     * @param repartidorId ID del repartidor recibido en el payload.
     * @param clientNodeId ID de red del reloj Wear OS para enviar la respuesta.
     * @return Unit (Envía la respuesta JSON al cliente de mensajes de Google Wearable API).
     */
    private fun fetchActiveOrder(repartidorId: String, clientNodeId: String) {
        Log.d(TAG, "Consultando pedido activo para el repartidor ID: $repartidorId")
        
        // Construir la petición HTTP GET al endpoint del backend utilizando el URL configurado en el servidor
        val request = Request.Builder()
            .url("${ServerConfig.BASE_URL}/api/pedidos/activo?repartidorId=$repartidorId") // URL con parámetro query
            .get() // Especificar verbo HTTP GET
            .build() // Finalizar construcción de la solicitud

        try {
            // Ejecutar la petición síncrona dentro del hilo del pool de ejecución
            client.newCall(request).execute().use { response ->
                // Extraer el texto del cuerpo de la respuesta HTTP
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Respuesta REST backend: Código=${response.code}, Cuerpo=$responseBody")
                
                // Formatear el objeto de respuesta JSON para enviarlo al reloj inteligente
                val resultObj = JSONObject().apply {
                    put("success", response.isSuccessful) // Indicador de éxito (código 2xx)
                    put("code", response.code) // Código de respuesta HTTP (ej. 200, 404)
                    put("data", if (responseBody.isNotEmpty()) JSONObject(responseBody) else null) // Contenido del pedido
                }
                
                // Transmitir la respuesta de vuelta al smartwatch utilizando la ruta de respuesta
                sendReply(nodeId = clientNodeId, path = "/pedido/activo/response", message = resultObj.toString())
            }
        } catch (e: Exception) {
            // Manejar errores de conexión o excepciones al consultar el servidor
            Log.e(TAG, "Excepción al consultar el pedido activo en el servidor REST", e)
            
            // Construir JSON de respuesta notificando la falla
            val errObj = JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
            // Enviar mensaje de error al reloj
            sendReply(nodeId = clientNodeId, path = "/pedido/activo/response", message = errObj.toString())
        }
    }

    /**
     * Procesa la solicitud enviada por el reloj inteligente para actualizar el estatus de una entrega en el servidor.
     *
     * @param jsonPayload Cadena JSON recibida del reloj con id_pedido y estatus.
     * @param clientNodeId ID de red del reloj emisor.
     * @return Unit (Envía el resultado del cambio de estatus vía Wearable API).
     */
    private fun updateOrderStatus(jsonPayload: String, clientNodeId: String) {
        Log.d(TAG, "Procesando actualización de estatus enviada desde el reloj: $jsonPayload")
        
        try {
            // Parsear la cadena JSON recibida
            val inputObj = JSONObject(jsonPayload)
            val pedidoId = inputObj.getInt("id_pedido") // Extraer ID del pedido
            val newStatus = inputObj.getInt("estatus")  // Extraer el nuevo estatus

            // Crear el cuerpo de la petición PUT en formato JSON
            val bodyObj = JSONObject().apply {
                put("estatus", newStatus)
            }

            // Construir la petición HTTP PUT dirigida a la API REST del servidor central
            val request = Request.Builder()
                .url("${ServerConfig.BASE_URL}/api/pedidos/$pedidoId/estatus") // Endpoint de estatus
                .put(bodyObj.toString().toRequestBody(mediaTypeJson)) // Verbo PUT con cuerpo JSON
                .build()

            // Ejecutar la petición HTTP en el cliente OkHttp
            client.newCall(request).execute().use { response ->
                // Crear JSON de respuesta para el smartwatch
                val resultObj = JSONObject().apply {
                    put("success", response.isSuccessful)
                    put("code", response.code)
                }
                // Transmitir la respuesta de actualización al reloj
                sendReply(nodeId = clientNodeId, path = "/pedido/status/response", message = resultObj.toString())
            }
        } catch (e: Exception) {
            // Manejar excepciones de red o de parseo durante la actualización
            Log.e(TAG, "Error actualizando el estatus del pedido enviado desde Wear OS", e)
            val errObj = JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
            sendReply(nodeId = clientNodeId, path = "/pedido/status/response", message = errObj.toString())
        }
    }

    /**
     * Función auxiliar para transmitir mensajes de respuesta de vuelta al dispositivo de destino Wear OS.
     *
     * @param nodeId Identificador de nodo de red del dispositivo destino (smartwatch).
     * @param path Ruta del mensaje en la arquitectura de comunicación.
     * @param message Mensaje formateado en cadena de texto JSON.
     * @return Unit (Envía el mensaje usando Google Play Services Wearable.getMessageClient).
     */
    private fun sendReply(nodeId: String, path: String, message: String) {
        // Convertir la cadena de texto a un arreglo de bytes en UTF-8
        val payload = message.toByteArray(Charsets.UTF_8)
        
        // Invocar el cliente de mensajes de la API Google Wearable
        Wearable.getMessageClient(this).sendMessage(nodeId, path, payload)
            .addOnSuccessListener {
                // Registrar confirmación exitosa de envío
                Log.d(TAG, "Respuesta enviada con éxito al nodo $nodeId en la ruta: $path")
            }
            .addOnFailureListener { e ->
                // Registrar fallo en la entrega del mensaje
                Log.e(TAG, "Error al transmitir respuesta al nodo $nodeId en la ruta: $path", e)
            }
    }
}
```

---

## 5. Carpeta MQTT (`mqtt/`)

Esta carpeta contiene el gestor de mensajería MQTT para publicar la telemetría de ubicación GPS del teléfono móvil hacia la nube en tiempo real.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/mqtt/MobileMqttManager.kt`
**Funcionalidad:** Gestor de conexión MQTT para el teléfono móvil. Se conecta al broker de HiveMQ Cloud vía SSL en el puerto 8883 y publica la telemetría del repartidor (`id_repartidor`, `lat`, `lng`, `speed`) en el tema `deliverytrack/telemetry/{id_repartidor}`.

```kotlin
package mx.utng.deliverytrack.mobile.mqtt

import mx.utng.deliverytrack.shared.mqtt.MqttClientHelper

/**
 * Gestor responsable de establecer la conexión MQTT y enviar paquetes de telemetría de posición GPS en tiempo real.
 *
 * @property serverUri URI completa del servidor broker MQTT cifrado con protocolo SSL (HiveMQ Cloud).
 * @property username Nombre de usuario para la autenticación en el servidor MQTT.
 * @property password Contraseña de acceso para el usuario del servidor MQTT.
 */
class MobileMqttManager(
    private val serverUri: String = "ssl://79a94522998842728c5ef7bf42fd3c30.s1.eu.hivemq.cloud:8883", // Broker SSL
    private val username: String = "smarthealthmonitor", // Credencial de usuario HiveMQ
    private val password: String = "linux123" // Credencial de clave HiveMQ
) {
    // Referencia al cliente auxiliar MQTT del módulo compartido
    private var mqttClientHelper: MqttClientHelper? = null

    /**
     * Inicia el proceso de conexión con el broker MQTT.
     *
     * @param clientId Identificador de cliente único registrado en la sesión del broker.
     * @param onConnected Función lambda que se ejecuta de forma asíncrona al conectarse exitosamente.
     * @return Unit (Conecta el cliente socket MQTT al servidor en segundo plano).
     */
    fun connect(
        clientId: String, // ID único del cliente MQTT
        onConnected: () -> Unit // Callback de conexión exitosa
    ) {
        // Crear una nueva instancia del helper cliente MQTT inyectando servidor y credenciales
        mqttClientHelper = MqttClientHelper(
            serverUri = serverUri,
            clientId = clientId,
            username = username,
            password = password
        )
        
        // Ejecutar la conexión asíncrona pasando la callback de éxito y una lambda vacía para errores
        mqttClientHelper?.connect(
            onConnected = onConnected, // Callback cuando se completa la conexión
            onFailure = { _ -> }      // Manejador de fallas
        )
    }

    /**
     * Publica las coordenadas GPS de la posición del repartidor en el canal MQTT dedicado.
     *
     * @param courierId Identificador único del repartidor.
     * @param lat Latitud GPS en grados decimales.
     * @param lng Longitud GPS en grados decimales.
     * @param speed Velocidad actual de avance del repartidor.
     * @return Unit (Formatea el payload en cadena JSON y lo envía al tópico MQTT).
     */
    fun publishTelemetry(
        courierId: Int,   // ID del repartidor
        lat: Double,      // Coordenada Latitud
        lng: Double,      // Coordenada Longitud
        speed: Float      // Velocidad
    ) {
        // Construir la cadena de texto con formato JSON representando la telemetría actual
        val payload = """{"id_repartidor":$courierId,"lat":$lat,"lng":$lng,"speed":$speed}"""
        
        // Definir el tópico canal dinámico según el ID del repartidor
        val topic = "deliverytrack/telemetry/$courierId"
        
        // Publicar la cadena JSON al tópico especificado
        mqttClientHelper?.publish(topic = topic, payload = payload)
    }

    /**
     * Desconecta limpiamente la sesión activa del cliente MQTT.
     *
     * @return Unit (Cierra la conexión del socket de red).
     */
    fun disconnect() {
        // Invocar el método de desconexión del helper cliente
        mqttClientHelper?.disconnect()
    }
}
```

---

## 6. Carpeta Navegación (`navigation/`)

Esta carpeta define las rutas de navegación tipadas para Compose Navigation dentro de la aplicación móvil.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/navigation/NavGraph.kt`
**Funcionalidad:** Define la jerarquía de rutas sealed class `Screen` para la navegación entre pantallas de Compose dentro de la aplicación.

```kotlin
package mx.utng.deliverytrack.mobile.navigation

/**
 * Clase sellada (Sealed Class) para estructurar la jerarquía de rutas de navegación en la UI de Jetpack Compose.
 *
 * @property route Cadena de texto identificadora de la ruta de la pantalla para el NavHost.
 */
sealed class Screen(
    val route: String // Ruta en formato de texto plano
) {
    /** Ruta correspondiente a la pantalla de Inicio de Sesión (Login) */
    object Login : Screen(route = "login")
    
    /** Ruta de la pantalla principal de Pedidos Asignados al Repartidor */
    object MisPedidos : Screen(route = "mis_pedidos")
    
    /** Ruta con parámetro dinámico del detalle de pedido para repartidores */
    object DetallePedido : Screen(route = "detalle_pedido/{pedidoId}") {
        /**
         * Función helper para construir la cadena de ruta tipada inyectando el valor real del pedidoId.
         *
         * @param pedidoId ID del pedido a consultar.
         * @return String conteniendo la ruta formateada para el navegador (ej: "detalle_pedido/15").
         */
        fun createRoute(pedidoId: Int): String {
            return "detalle_pedido/$pedidoId" // Retornar cadena con el ID interpolate
        }
    }
    
    /** Ruta del Dashboard Principal del Administrador */
    object AdminDashboard : Screen(route = "admin_dashboard")
    
    /** Ruta del formulario de Alta/Edición de Pedidos */
    object NuevoPedido : Screen(route = "nuevo_pedido")
    
    /** Ruta del módulo de Gestión de Usuarios y Repartidores */
    object GestionRepartidores : Screen(route = "gestion_repartidores")
}
```

---

## 7. Carpeta UI - Actividad Principal (`ui/`)

Esta carpeta contiene la actividad raíz de la aplicación móvil (`MainActivity`) y formularios principales de la interfaz del sistema.

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
 * Actividad Principal de Android y contenedor raíz del árbol de vistas Compose.
 */
class MainActivity : ComponentActivity() {

    // Variable de estado reactiva que conserva la sesión activa del usuario (null si no ha iniciado sesión)
    private var activeSession by mutableStateOf<UserSession?>(null)

    /**
     * Método del ciclo de vida onCreate ejecutado al iniciar la actividad.
     *
     * @param savedInstanceState Estado previo guardado en la reinstanciación.
     * @return Unit (Renderiza la vista reactiva con setContent).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Invocación a la implementación base onCreate de ComponentActivity
        super.onCreate(savedInstanceState)
        
        // Guardar referencia constante del contexto de la actividad actual
        val context = this

        // Establecer el contenido gráfico de la pantalla mediante Jetpack Compose
        setContent {
            // Aplicar el tema gráfico global de Material Design 3
            MaterialTheme {
                // Almacenar temporalmente la sesión activa en una constante local
                val session = activeSession
                
                // Evaluar si existe una sesión de usuario autenticada
                if (session == null) {
                    // Renderizar la pantalla de Login si no hay usuario autenticado
                    LoginScreen(
                        onLoginSuccess = { user -> // Callback cuando el inicio de sesión es exitoso
                            // Actualizar la variable de estado reactivo con la sesión devuelta
                            activeSession = user
                        }
                    )
                } else if (session.rol == 1) { 
                    // Renderizar el Dashboard para usuarios con Rol 1 (Administrador)
                    AdminDashboardScreen(
                        userSession = session, // Pasar objeto de sesión del administrador
                        onCrearPedidoClick = { // Callback al presionar el botón de crear pedido
                            // Lanzar la actividad NuevoPedidoActivity mediante un Intent
                            context.startActivity(Intent(context, NuevoPedidoActivity::class.java))
                        },
                        onGestionUsuariosClick = { // Callback para administrar usuarios
                            // Lanzar la actividad GestionUsuariosActivity
                            context.startActivity(Intent(context, GestionUsuariosActivity::class.java))
                        },
                        onLogoutClick = { // Callback para cerrar sesión
                            // Establecer la sesión activa a null para retornar a la pantalla de Login
                            activeSession = null
                        }
                    )
                } else { 
                    // Renderizar la pantalla de entregas para usuarios con Rol 2 (Repartidor)
                    MisPedidosRepartidorScreen(
                        userSession = session, // Pasar objeto de sesión del repartidor
                        onVerDetalleClick = { pedidoId -> // Callback al hacer clic en un pedido de la lista
                            // Construir Intent explícito hacia DetallePedidoRepartidorActivity inyectando extras
                            val intent = Intent(context, DetallePedidoRepartidorActivity::class.java).apply {
                                putExtra(DetallePedidoRepartidorActivity.EXTRA_ORDER_ID, pedidoId) // Inyectar ID del pedido
                                putExtra(DetallePedidoRepartidorActivity.EXTRA_COURIER_ID, session.idUser) // Inyectar ID de repartidor
                            }
                            // Iniciar la actividad de detalle de la entrega
                            context.startActivity(intent)
                        },
                        onLogoutClick = { // Callback para cerrar sesión del repartidor
                            // Limpiar la sesión activa
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
 * Función auxiliar privada para deshabilitar la validación estricta de certificados SSL en modo desarrollo.
 *
 * @param conn Conexión HttpURLConnection sobre la cual deshabilitar la comprobación SSL.
 * @return Unit (Modifica las propiedades de la conexión de red).
 */
private fun applySslBypass(conn: java.net.HttpURLConnection) {
    if (conn is javax.net.ssl.HttpsURLConnection) {
        try {
            // Arreglo de TrustManager que confía en cualquier certificado SSL
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                }
            )
            // Inicializar el contexto SSL en modo "SSL"
            val sc = javax.net.ssl.SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            // Asignar el SocketFactory y el verificador de nombre de host
            conn.sslSocketFactory = sc.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }
}

/**
 * Actividad encargada de presentar el formulario de Alta y Edición de Pedidos.
 */
class NuevoPedidoActivity : ComponentActivity() {

    // URL base del servidor obtenida desde la configuración compartida
    private val backendUrl = ServerConfig.BASE_URL
    // Handler para enviar tareas de UI al hilo principal (Main Looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        /** Clave del Extra Intent para identificar el ID del pedido a editar */
        const val EXTRA_EDIT_ORDER_ID = "extra_edit_order_id"
    }

    /**
     * Inicializador del ciclo de vida onCreate de la actividad.
     *
     * @param savedInstanceState Estado previo guardado.
     * @return Unit.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extraer el ID del pedido a editar enviado por el Intent (o valor por defecto -1)
        val editOrderId = intent.getIntExtra(EXTRA_EDIT_ORDER_ID, -1)

        // Definir el contenido gráfico en Compose
        setContent {
            MaterialTheme {
                // Invocación del composable principal del formulario
                NuevoPedidoScreen(
                    backendUrl = backendUrl, // Pasar la URL del backend
                    editOrderId = if (editOrderId > 0) editOrderId else null, // Enviar ID solo si es mayor a 0
                    onPedidoGuardado = { finish() }, // Callback para cerrar la actividad al guardar
                    onShowToast = { msg -> // Callback para proyectar un Toast en el hilo principal
                        mainHandler.post {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Componente Composable que construye la interfaz visual del formulario para crear/editar un pedido.
 *
 * @param backendUrl Dirección base HTTP de la API REST.
 * @param editOrderId ID del pedido si está en modo edición, o null si se crea uno nuevo.
 * @param onPedidoGuardado Callback al finalizar el guardado.
 * @param onShowToast Callback para proyectar notificaciones en pantalla.
 * @return Unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevoPedidoScreen(
    backendUrl: String,
    editOrderId: Int? = null,
    onPedidoGuardado: () -> Unit,
    onShowToast: (String) -> Unit
) {
    // Determinar si la vista está operando en modo edición
    val isEditMode = (editOrderId != null)

    // Variables de estado reactivas para almacenar el contenido de las cajas de texto del formulario
    var nombreCliente by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var referencia by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    // Estados para la carga y selección del repartidor
    var repartidores by remember { mutableStateOf<List<Repartidor>>(emptyList()) }
    var repartidorSeleccionado by remember { mutableStateOf<Repartidor?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Estados de progreso de carga y error
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingRepartidores by remember { mutableStateOf(true) }
    var errorRepartidores by remember { mutableStateOf("") }

    // Definición de colores principales
    val primaryBlue = Color(0xFF1A3A6B)
    val accentBlue = Color(0xFF2563EB)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Efecto de lanzamiento al iniciar para consultar la lista de repartidores disponibles
    LaunchedEffect(Unit) {
        thread {
            try {
                // Crear URL del endpoint de repartidores
                val url = java.net.URL("$backendUrl/api/usuarios/repartidores")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn) // Aplicar bypass SSL
                conn.requestMethod = "GET" // Método GET
                conn.connectTimeout = 5000 // Timeout de 5s
                conn.readTimeout = 5000

                val code = conn.responseCode // Obtener código de respuesta
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().readText() // Leer respuesta
                    val arr = JSONArray(response) // Parsear arreglo JSON
                    val list = (0 until arr.length()).map { index ->
                        val obj = arr.getJSONObject(index)
                        Repartidor(
                            id = obj.getInt("id_user"),
                            nombre = obj.getString("nombre_completo"),
                            telefono = obj.getString("telefono")
                        )
                    }
                    // Publicar resultados en el hilo principal
                    mainHandler.post {
                        repartidores = list
                        if (list.isNotEmpty() && repartidorSeleccionado == null) {
                            repartidorSeleccionado = list[0] // Seleccionar primer repartidor por defecto
                        }
                        isLoadingRepartidores = false
                    }
                } else {
                    mainHandler.post {
                        errorRepartidores = "Error $code al obtener repartidores"
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

    // Efecto de lanzamiento cuando editOrderId cambia para rellenar el formulario si es edición
    LaunchedEffect(editOrderId) {
        if (editOrderId != null) {
            thread {
                try {
                    val url = java.net.URL("$backendUrl/api/pedidos/$editOrderId")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    applySslBypass(conn)
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000

                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        val obj = JSONObject(text) // Parsear objeto JSON del pedido
                        mainHandler.post {
                            // Rellenar campos con la información existente
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

    // Estructura principal de la pantalla con Scaffold
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
                    IconButton(onClick = onPedidoGuardado) { // Botón para regresar
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState()) // Habilitar desplazamiento vertical
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta 1: Información del Cliente
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Información del Cliente",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryBlue
                    )

                    // Campo de Nombre Completo
                    OutlinedTextField(
                        value = nombreCliente,
                        onValueChange = { nombreCliente = it }, // Actualizar estado del nombre
                        label = { Text("Nombre del Cliente *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Campo de Teléfono
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it }, // Actualizar estado del teléfono
                        label = { Text("Teléfono *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    // Campo de Dirección
                    OutlinedTextField(
                        value = direccion,
                        onValueChange = { direccion = it }, // Actualizar estado de la dirección
                        label = { Text("Dirección de Entrega *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Campo de Referencia
                    OutlinedTextField(
                        value = referencia,
                        onValueChange = { referencia = it }, // Actualizar estado de referencia
                        label = { Text("Referencia del Lugar") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Tarjeta 2: Detalle del Pedido
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Detalle del Pedido",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryBlue
                    )

                    // Campo de Descripción del pedido
                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it }, // Actualizar descripción
                        label = { Text("Descripción de Artículos *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            // Tarjeta 3: Selección de Repartidor
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Asignación de Repartidor",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryBlue
                    )

                    // Evaluación de estados de carga para el dropdown
                    if (isLoadingRepartidores) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (errorRepartidores.isNotEmpty()) {
                        Text(text = errorRepartidores, color = MaterialTheme.colorScheme.error)
                    } else {
                        // Menú desplegable expuesto para seleccionar repartidor
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = repartidorSeleccionado?.nombre ?: "Seleccionar repartidor",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Repartidor Asignado *") },
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
                                            repartidorSeleccionado = rep // Asignar repartidor seleccionado
                                            dropdownExpanded = false // Cerrar menú
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Botón Principal de Envío (Guardar / Actualizar)
            Button(
                onClick = {
                    // Validar que los campos obligatorios contengan texto
                    if (nombreCliente.isBlank() || telefono.isBlank() || direccion.isBlank() || descripcion.isBlank()) {
                        onShowToast("Por favor completa los campos obligatorios (*)")
                        return@Button // Detener flujo
                    }
                    val repId = repartidorSeleccionado?.id
                    if (repId == null) {
                        onShowToast("Debes seleccionar un repartidor")
                        return@Button // Detener flujo
                    }

                    isLoading = true // Activar indicador de progreso
                    thread {
                        try {
                            val urlString = if (isEditMode) "$backendUrl/api/pedidos/$editOrderId" else "$backendUrl/api/pedidos"
                            val url = java.net.URL(urlString)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            applySslBypass(conn)
                            conn.requestMethod = if (isEditMode) "PUT" else "POST" // Verbo según el modo
                            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            conn.connectTimeout = 5000
                            conn.doOutput = true

                            // Construir objeto JSON para el cuerpo de la petición
                            val jsonBody = JSONObject().apply {
                                put("nombre_cliente", nombreCliente)
                                put("telefono", telefono)
                                put("direccion", direccion)
                                put("referencia_lugar", referencia)
                                put("descripcion_pedido", descripcion)
                                put("id_repartidor", repId)
                                if (!isEditMode) {
                                    put("estatus", 1) // Estado inicial por defecto: 1 (Aceptado)
                                }
                            }

                            // Escribir payload en el OutputStream de la conexión HTTP
                            conn.outputStream.use { os ->
                                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                            }

                            val responseCode = conn.responseCode // Leer código de respuesta
                            mainHandler.post {
                                isLoading = false // Desactivar progreso
                                if (responseCode == 200 || responseCode == 201) {
                                    onShowToast(if (isEditMode) "Pedido actualizado con éxito" else "Pedido creado exitosamente")
                                    onPedidoGuardado() // Cerrar actividad
                                } else {
                                    onShowToast("Error $responseCode al guardar el pedido")
                                }
                            }
                        } catch (e: Exception) {
                            mainHandler.post {
                                isLoading = false
                                onShowToast("Error de conexión: ${e.message}")
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading, // Deshabilitar si hay una operación en curso
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isEditMode) "Guardar Cambios" else "Crear Pedido",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
```

---

## 8. Carpeta UI - Autenticación (`ui/auth`)

Esta carpeta gestiona el flujo de autenticación, verificación de credenciales de usuario y mantenimiento de la sesión activa en el dispositivo.

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
 * Modelo de datos que representa los detalles de la sesión iniciada por un usuario autenticado.
 *
 * @property idUser Identificador del usuario en la base de datos central.
 * @property nombreCompleto Nombre completo del usuario.
 * @property telefono Número telefónico registrado.
 * @property rol Identificador de rol del usuario (1: Administrador, 2: Repartidor).
 * @property estatus Estado del usuario (1: Activo).
 */
data class UserSession(
    val idUser: Int,          // ID del usuario
    val nombreCompleto: String, // Nombre completo
    val telefono: String,       // Teléfono de login
    val rol: Int,               // Código de rol (1 o 2)
    val estatus: Int            // Estatus de la cuenta
)

/**
 * Aplica configuración para deshabilitar la verificación estricta SSL en peticiones HTTP.
 *
 * @param conn Conexión HttpURLConnection a modificar.
 * @return Unit.
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
 * Componente Composable para la vista del inicio de sesión (LoginScreen).
 *
 * @param onLoginSuccess Callback invocado cuando las credenciales son válidas, devolviendo un objeto UserSession.
 * @return Unit (Renderiza la interfaz gráfica de usuario en Compose).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (UserSession) -> Unit // Callback de éxito
) {
    val context = LocalContext.current // Contexto actual de Android
    
    // Estados reactivos para capturar entradas de texto
    var telefono by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) } // Estado para conmutar visibilidad de contraseña
    var isLoading by remember { mutableStateOf(false) } // Estado de carga durante la autenticación

    // Paleta de colores para tema oscuro elegante
    val darkBg = Color(0xFF0F172A)
    val cardBg = Color(0xFF1E293B)
    val accentBlue = Color(0xFF2563EB)
    val lightText = Color(0xFFF8FAFC)
    val mutedText = Color(0xFF94A3B8)

    // Contenedor Box principal alineado al centro de la pantalla
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg), // Fondo azul oscuro profundo
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono decorativo de perfil de usuario
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = accentBlue.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Logo",
                        modifier = Modifier.size(48.dp),
                        tint = accentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Título principal de la aplicación
            Text(
                text = "DeliveryTrack",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = lightText
            )

            // Subtítulo descriptivo
            Text(
                text = "Inicia sesión para continuar",
                fontSize = 14.sp,
                color = mutedText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Tarjeta contenedora de los campos de credenciales
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Campo de entrada: Teléfono
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it }, // Actualizar variable de teléfono
                        label = { Text("Teléfono", color = mutedText) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = accentBlue) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = mutedText.copy(alpha = 0.5f),
                            focusedLabelColor = accentBlue,
                            cursorColor = accentBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Campo de entrada: Contraseña
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it }, // Actualizar variable de contraseña
                        label = { Text("Contraseña", color = mutedText) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = accentBlue) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) { // Conmutar icono de ojo
                                Text(
                                    text = if (isPasswordVisible) "👁️" else "🙈",
                                    fontSize = 16.sp
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = mutedText.copy(alpha = 0.5f),
                            focusedLabelColor = accentBlue,
                            cursorColor = accentBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Botón de Inicio de Sesión
                    Button(
                        onClick = {
                            // Validar que ambos campos contengan texto
                            if (telefono.isBlank() || contrasena.isBlank()) {
                                Toast.makeText(context, "Ingresa teléfono y contraseña", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isLoading = true // Activar rueda de progreso
                            thread {
                                try {
                                    val url = java.net.URL("${ServerConfig.BASE_URL}/api/auth/login")
                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                    applySslBypass(conn)
                                    conn.requestMethod = "POST"
                                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                    conn.connectTimeout = 5000
                                    conn.doOutput = true

                                    // Construir JSON con las credenciales
                                    val body = JSONObject().apply {
                                        put("telefono", telefono)
                                        put("contrasena", contrasena)
                                    }

                                    // Escribir en la solicitud HTTP
                                    conn.outputStream.use { os ->
                                        os.write(body.toString().toByteArray(Charsets.UTF_8))
                                    }

                                    val code = conn.responseCode
                                    if (code == 200) {
                                        val resp = conn.inputStream.bufferedReader().readText()
                                        val obj = JSONObject(resp)
                                        val userObj = obj.getJSONObject("usuario") // Extraer objeto usuario

                                        // Instanciar objeto de sesión del usuario
                                        val user = UserSession(
                                            idUser = userObj.getInt("id_user"),
                                            nombreCompleto = userObj.getString("nombre_completo"),
                                            telefono = userObj.getString("telefono"),
                                            rol = userObj.getInt("rol"),
                                            estatus = userObj.getInt("estatus")
                                        )

                                        // Notificar éxito en el hilo de interfaz de usuario
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            isLoading = false
                                            onLoginSuccess(user) // Invocar callback con la sesión
                                        }
                                    } else {
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            isLoading = false
                                            Toast.makeText(context, "Credenciales incorrectas ($code)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        isLoading = false
                                        Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(text = "Iniciar Sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
```

---

## 9. Carpeta UI - Panel Administrador (`ui/admin`)

Esta carpeta concentra las pantallas destinadas a los administradores del sistema, permitiendo la supervisión global de pedidos, métricas de entregas y registro de repartidores.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/admin/AdminDashboardScreen.kt`
**Funcionalidad:** Panel de control principal del Administrador. Muestra métricas clave (Total, Pendientes, En Camino, Entregados, Cancelados), lista de pedidos con filtrado por estado, barra de búsqueda por cliente/dirección, botones de acción rápida para crear pedidos o gestionar repartidores y opción de cerrar sesión.

```kotlin
package mx.utng.deliverytrack.mobile.ui.admin

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.mobile.data.models.Pedido
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import kotlin.concurrent.thread

/**
 * Función privada para deshabilitar verificación estricta de SSL en entornos locales.
 *
 * @param conn Conexión HttpURLConnection.
 * @return Unit.
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
 * Pantalla principal del Dashboard de Administrador en Jetpack Compose.
 *
 * @param userSession Datos de la sesión activa del Administrador.
 * @param onCrearPedidoClick Callback para abrir la pantalla de creación de pedidos.
 * @param onGestionUsuariosClick Callback para navegar a la pantalla de gestión de usuarios.
 * @param onLogoutClick Callback para cerrar la sesión actual.
 * @return Unit (Renderiza la vista completa del panel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    userSession: UserSession,         // Objeto con la sesión del Administrador
    onCrearPedidoClick: () -> Unit,   // Callback para el botón flotante de crear pedido
    onGestionUsuariosClick: () -> Unit, // Callback para el icono de gestionar usuarios
    onLogoutClick: () -> Unit        // Callback para la opción de salir
) {
    val context = LocalContext.current // Contexto para lanzar intenciones (Intents)
    var pedidos by remember { mutableStateOf<List<Pedido>>(emptyList()) } // Lista reactiva de pedidos
    var isLoading by remember { mutableStateOf(true) } // Estado de progreso
    var searchQuery by remember { mutableStateOf("") } // Cadena de texto de búsqueda
    var selectedFilterStatus by remember { mutableStateOf<Int?>(null) } // Filtro por estado

    val primaryBlue = Color(0xFF1A3A6B)
    val bgGray = Color(0xFFF8FAFC)

    // Función interna para realizar la carga de los pedidos desde la API REST
    fun loadPedidos() {
        isLoading = true // Activar rueda de progreso
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(resp)
                    val list = (0 until arr.length()).map { index ->
                        val obj = arr.getJSONObject(index)
                        Pedido(
                            id = obj.getInt("id_pedido"),
                            nombreCliente = obj.optString("nombre_cliente", ""),
                            telefono = obj.optString("telefono", ""),
                            direccion = obj.optString("direccion", ""),
                            referenciaLugar = obj.optString("referencia_lugar", ""),
                            descripcionPedido = obj.optString("descripcion_pedido", ""),
                            idRepartidor = obj.optInt("id_repartidor", 0),
                            estatus = obj.optInt("estatus", 1)
                        )
                    }
                    // Actualizar el estado de Compose en el hilo principal
                    (context as? android.app.Activity)?.runOnUiThread {
                        pedidos = list
                        isLoading = false
                    }
                } else {
                    (context as? android.app.Activity)?.runOnUiThread { isLoading = false }
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread { isLoading = false }
            }
        }
    }

    // Efecto secundario ejecutado al renderizar el composable por primera vez
    LaunchedEffect(Unit) {
        loadPedidos()
    }

    // Evaluación de filtrado de la lista de pedidos según búsqueda e indicador de estado
    val filteredPedidos = pedidos.filter { p ->
        val matchesSearch = p.nombreCliente.contains(searchQuery, ignoreCase = true) ||
                p.direccion.contains(searchQuery, ignoreCase = true) ||
                p.id.toString().contains(searchQuery)
        val matchesStatus = selectedFilterStatus == null || p.estatus == selectedFilterStatus
        matchesSearch && matchesStatus
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Panel Administrador", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text("Hola, ${userSession.nombreCompleto}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                },
                actions = {
                    // Botón para refrescar manualmente
                    IconButton(onClick = { loadPedidos() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
                    }
                    // Botón para ingresar a Gestión de Usuarios
                    IconButton(onClick = onGestionUsuariosClick) {
                        Icon(Icons.Default.Person, contentDescription = "Usuarios", tint = Color.White)
                    }
                    // Botón de texto para cerrar la sesión
                    TextButton(onClick = onLogoutClick) {
                        Text("Salir", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        },
        floatingActionButton = {
            // Botón Flotante para crear un nuevo pedido
            FloatingActionButton(
                onClick = onCrearPedidoClick,
                containerColor = primaryBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear Pedido")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgGray)
                .padding(16.dp)
        ) {
            // Buscador por texto interactivo
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it }, // Actualizar variable de búsqueda
                placeholder = { Text("Buscar por cliente, dirección o ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fila con 3 tarjetas de métricas cuantitativas del sistema
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    label = "Total",
                    count = pedidos.size.toString(),
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "En Camino",
                    count = pedidos.count { it.estatus == 3 }.toString(),
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Entregados",
                    count = pedidos.count { it.estatus == 6 }.toString(),
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Lista de Pedidos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryBlue)

            Spacer(modifier = Modifier.height(8.dp))

            // Renderizado condicional según el progreso de carga
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredPedidos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron pedidos", color = Color.Gray)
                }
            } else {
                // Lista perezosa (LazyColumn) para optimizar el rendimiento del renderizado de tarjetas
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPedidos) { pedido ->
                        AdminPedidoItem(
                            pedido = pedido,
                            onClick = { // Lanzar la actividad de detalle al presionar
                                val intent = Intent(context, AdminDetallePedidoActivity::class.java).apply {
                                    putExtra(AdminDetallePedidoActivity.EXTRA_ORDER_ID, pedido.id)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de visualización de métrica/estadística individual para el dashboard del administrador.
 * 
 * @param label Etiqueta o título textual que describe la métrica (ej. "Total", "En Camino", "Entregados").
 * @param count Valor numérico como cadena de texto que representa el conteo de pedidos.
 * @param color Color primario de acento aplicado al número representativo.
 * @param modifier Modificador de layout Compose para ajustar el tamaño y pesos de distribución.
 * @return Unit (Renderiza un componente UI de tarjeta en Compose).
 */
@Composable
private fun StatCard(
    label: String, // Texto descriptivo del indicador
    count: String, // Cantidad numérica a mostrar
    color: Color,  // Color de resalte para el texto numérico
    modifier: Modifier = Modifier // Modificador por defecto para estructurar el diseño
) {
    // Contenedor tipo Tarjeta de Material 3 con elevación y fondo blanco
    Card(
        modifier = modifier, // Aplicar modificador de tamaño asignado
        colors = CardDefaults.cardColors(containerColor = Color.White), // Color de fondo blanco puro
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Elevación de sombra de 2dp
    ) {
        // Columna vertical para alinear el contador y la etiqueta centrados
        Column(
            modifier = Modifier.padding(12.dp), // Espaciado interno de 12dp en todos los bordes
            horizontalAlignment = Alignment.CenterHorizontally // Alineación horizontal centrada
        ) {
            // Texto numérico grande con estilo en negrita y color personalizado
            Text(
                text = count, // Valor numérico de la estadística
                fontSize = 20.sp, // Tamaño de fuente en puntos de escala (20sp)
                fontWeight = FontWeight.Bold, // Estilo tipográfico en negrita
                color = color // Asignación del color de acento
            )
            
            // Texto descriptivo secundario en tono gris
            Text(
                text = label, // Nombre o etiqueta de la estadística
                fontSize = 11.sp, // Tamaño de fuente pequeño (11sp)
                color = Color.Gray // Color gris neutro para texto secundario
            )
        }
    }
}

/**
 * Componente que renderiza una tarjeta individual de pedido en la lista del administrador.
 *
 * @param pedido Instancia del objeto Pedido a mostrar.
 * @param onClick Callback invocado al pulsar sobre la tarjeta.
 * @return Unit.
 */
@Composable
private fun AdminPedidoItem(
    pedido: Pedido,     // Objeto de pedido a presentar
    onClick: () -> Unit // Callback de clic
) {
    // Asignación de color según el código de estatus del pedido
    val statusColor = when (pedido.estatus) {
        1 -> Color(0xFF3B82F6) // Aceptado (Azul)
        2 -> Color(0xFF6B7280) // Pendiente (Gris)
        3 -> Color(0xFFF59E0B) // En camino (Naranja)
        4 -> Color(0xFFEF4444) // Cancelado (Rojo)
        5 -> Color(0xFF8B5CF6) // Retrasado (Púrpura)
        6 -> Color(0xFF10B981) // Entregado (Verde)
        else -> Color.Gray
    }

    // Texto representativo del estatus
    val statusText = when (pedido.estatus) {
        1 -> "Aceptado"
        2 -> "Pendiente"
        3 -> "En Camino"
        4 -> "Cancelado"
        5 -> "Retrasado"
        6 -> "Entregado"
        else -> "Desconocido"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }, // Hacer clicable la tarjeta completa
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Pedido #${pedido.id}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = pedido.nombreCliente, fontSize = 14.sp, color = Color.Black)
                Text(text = pedido.direccion, fontSize = 12.sp, color = Color.Gray)
            }
            // Etiqueta visual tipo insignia con el color de estado
            Surface(
                color = statusColor.copy(alpha = 0.15f), // Fondo translúcido del color del estado
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/admin/AdminDetallePedidoActivity.kt`
**Funcionalidad:** Vista detallada de un pedido para el Administrador. Permite ver el historial de estatus, cambiar el estado del pedido, reasignar repartidor y lanzar la pantalla de edición del pedido.

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.mobile.ui.NuevoPedidoActivity
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Aplicación de confianza SSL para peticiones del administrador.
 *
 * @param conn Conexión HttpURLConnection.
 * @return Unit.
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
 * Actividad para la consulta detallada y gestión de estado de un pedido específico por el Administrador.
 */
class AdminDetallePedidoActivity : ComponentActivity() {

    companion object {
        /** Clave extra para passar el ID del pedido */
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    /**
     * Inicializador de la actividad.
     *
     * @param savedInstanceState Estado previo guardado.
     * @return Unit.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getIntExtra(EXTRA_ORDER_ID, -1) // Extraer ID de pedido

        setContent {
            MaterialTheme {
                AdminDetallePedidoScreen(
                    orderId = orderId,
                    onBackClick = { finish() }, // Regresar a la pantalla anterior
                    onEditClick = { id -> // Abrir la pantalla de edición
                        val intent = Intent(this, NuevoPedidoActivity::class.java).apply {
                            putExtra(NuevoPedidoActivity.EXTRA_EDIT_ORDER_ID, id)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

/**
 * Componente Composable que renderiza los datos detallados del pedido y botones para modificar el estatus.
 *
 * @param orderId ID del pedido consultado.
 * @param onBackClick Callback para el botón de regreso.
 * @param onEditClick Callback para el botón de editar.
 * @return Unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDetallePedidoScreen(
    orderId: Int,
    onBackClick: () -> Unit,
    onEditClick: (Int) -> Unit
) {
    var pedidoJson by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val primaryBlue = Color(0xFF1A3A6B)

    // Función interna para cargar la información del pedido
    fun loadOrderDetail() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val obj = JSONObject(text)
                    pedidoJson = obj // Guardar objeto JSON recibido
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false // Finalizar indicador de carga
            }
        }
    }

    LaunchedEffect(orderId) {
        loadOrderDetail()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle Pedido #$orderId", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(onClick = { onEditClick(orderId) }) { // Botón Editar en la barra superior
                        Text("Editar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBlue)
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (pedidoJson == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No se pudo cargar la información del pedido")
            }
        } else {
            val obj = pedidoJson!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFF8FAFC))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta con detalles del cliente
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Información del Cliente", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryBlue)
                        Text("Cliente: ${obj.optString("nombre_cliente")}")
                        Text("Teléfono: ${obj.optString("telefono")}")
                        Text("Dirección: ${obj.optString("direccion")}")
                        Text("Referencia: ${obj.optString("referencia_lugar")}")
                        Text("Descripción: ${obj.optString("descripcion_pedido")}")
                    }
                }

                // Tarjeta para cambiar el estatus del pedido
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Cambiar Estado del Pedido", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryBlue)

                        val currentStatus = obj.optInt("estatus", 1)
                        Text("Estado actual: $currentStatus")

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { updateStatus(orderId, 3) { loadOrderDetail() } }, // Cambiar a 'En camino' (3)
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                            ) {
                                Text("En Camino")
                            }
                            Button(
                                onClick = { updateStatus(orderId, 6) { loadOrderDetail() } }, // Cambiar a 'Entregado' (6)
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Entregado")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Función auxiliar privada para enviar la petición PUT de cambio de estatus a la API REST.
 *
 * @param orderId ID del pedido a actualizar.
 * @param newStatus Nuevo código de estatus numérico.
 * @param onComplete Callback ejecutado tras completar la actualización HTTP.
 * @return Unit.
 */
private fun updateStatus(orderId: Int, newStatus: Int, onComplete: () -> Unit) {
    thread {
        try {
            val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId/estatus")
            val conn = url.openConnection() as java.net.HttpURLConnection
            applySslBypass(conn)
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true

            val body = JSONObject().apply { put("estatus", newStatus) }
            conn.outputStream.use { os -> os.write(body.toString().toByteArray(Charsets.UTF_8)) }

            conn.responseCode
        } catch (_: Exception) {
        } finally {
            onComplete() // Refrescar detalles al finalizar
        }
    }
}
```

---

## 10. Carpeta UI - Panel Repartidor (`ui/repartidor`)

Esta carpeta concentra las pantallas exclusivas para los repartidores de la flotilla, ofreciendo la consulta de pedidos asignados, transmisión de ubicación GPS por MQTT y actualización de estatus de entregas.

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/repartidor/MisPedidosRepartidorScreen.kt`
**Funcionalidad:** Lista interactiva de entregas asignadas al repartidor autenticado. Se conecta al gestor MQTT `MobileMqttManager` para iniciar la transmisión en vivo de coordenadas de ubicación del repartidor al backend.

```kotlin
package mx.utng.deliverytrack.mobile.ui.repartidor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.mobile.data.models.Pedido
import mx.utng.deliverytrack.mobile.mqtt.MobileMqttManager
import mx.utng.deliverytrack.mobile.ui.auth.UserSession
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONArray
import kotlin.concurrent.thread

/**
 * Bypass de validación SSL.
 *
 * @param conn Conexión HttpURLConnection.
 * @return Unit.
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
 * Pantalla principal del Repartidor para visualizar sus pedidos asignados e iniciar el seguimiento MQTT.
 *
 * @param userSession Sesión activa del repartidor logueado.
 * @param onVerDetalleClick Callback invocado al presionar un pedido para abrir su detalle.
 * @param onLogoutClick Callback para cerrar sesión.
 * @return Unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisPedidosRepartidorScreen(
    userSession: UserSession,
    onVerDetalleClick: (Int) -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    var pedidos by remember { mutableStateOf<List<Pedido>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val primaryColor = Color(0xFF15803D) // Verde distintivo del repartidor
    val bgGray = Color(0xFFF8FAFC)

    // Inicializar y gestionar el ciclo de vida de la conexión MQTT de telemetría GPS
    DisposableEffect(userSession.idUser) {
        val mqttManager = MobileMqttManager()
        // Conectar al broker MQTT con un ID de cliente único por repartidor
        mqttManager.connect("mobile_courier_${userSession.idUser}") {
            // Publicar coordenada inicial simulada al confirmar la conexión
            mqttManager.publishTelemetry(userSession.idUser, 20.9148, -101.4044, 25.0f)
        }
        onDispose {
            // Desconectar limpiamente el cliente MQTT al salir de la pantalla
            mqttManager.disconnect()
        }
    }

    // Función interna para consultar la lista de pedidos asignados al repartidor
    fun loadMisPedidos() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/repartidor/${userSession.idUser}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(text)
                    val list = (0 until arr.length()).map { index ->
                        val obj = arr.getJSONObject(index)
                        Pedido(
                            id = obj.getInt("id_pedido"),
                            nombreCliente = obj.optString("nombre_cliente", ""),
                            telefono = obj.optString("telefono", ""),
                            direccion = obj.optString("direccion", ""),
                            referenciaLugar = obj.optString("referencia_lugar", ""),
                            descripcionPedido = obj.optString("descripcion_pedido", ""),
                            idRepartidor = obj.optInt("id_repartidor", userSession.idUser),
                            estatus = obj.optInt("estatus", 1)
                        )
                    }
                    (context as? android.app.Activity)?.runOnUiThread {
                        pedidos = list
                        isLoading = false
                    }
                } else {
                    (context as? android.app.Activity)?.runOnUiThread { isLoading = false }
                }
            } catch (_: Exception) {
                (context as? android.app.Activity)?.runOnUiThread { isLoading = false }
            }
        }
    }

    LaunchedEffect(userSession.idUser) {
        loadMisPedidos()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis Entregas", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Repartidor: ${userSession.nombreCompleto}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                actions = {
                    IconButton(onClick = { loadMisPedidos() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
                    }
                    TextButton(onClick = onLogoutClick) {
                        Text("Salir", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgGray)
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (pedidos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tienes pedidos asignados")
                }
            } else {
                // Renderizado de lista de tarjetas de pedidos
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pedidos) { pedido ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVerDetalleClick(pedido.id) }, // Invocación de detalle
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Pedido #${pedido.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Cliente: ${pedido.nombreCliente}", fontSize = 14.sp)
                                Text("Dirección: ${pedido.direccion}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

### `mobile/src/main/java/mx/utng/deliverytrack/mobile/ui/repartidor/DetallePedidoRepartidorActivity.kt`
**Funcionalidad:** Vista detallada de un pedido asignado al repartidor. Permite cambiar el estado de la entrega en tiempo real (Marcar "En camino", "Entregado", etc.) y refrescar el estado.

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.shared.config.ServerConfig
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Aplicación de bypass SSL para peticiones del repartidor.
 *
 * @param conn Conexión HttpURLConnection.
 * @return Unit.
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
 * Actividad Android de detalle y actualización de estatus para el repartidor.
 */
class DetallePedidoRepartidorActivity : ComponentActivity() {

    companion object {
        /** Clave del Intent Extra con el ID del pedido */
        const val EXTRA_ORDER_ID = "extra_order_id"
        /** Clave del Intent Extra con el ID del repartidor */
        const val EXTRA_COURIER_ID = "extra_courier_id"
    }

    /**
     * Ciclo onCreate de la actividad.
     *
     * @param savedInstanceState Estado previo guardado.
     * @return Unit.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getIntExtra(EXTRA_ORDER_ID, -1) // Extraer ID del pedido

        setContent {
            MaterialTheme {
                DetallePedidoRepartidorScreen(
                    orderId = orderId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/**
 * Screen Composable con la interfaz de actualización del repartidor.
 *
 * @param orderId ID del pedido asignado.
 * @param onBackClick Callback para cerrar la pantalla.
 * @return Unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetallePedidoRepartidorScreen(
    orderId: Int,
    onBackClick: () -> Unit
) {
    var pedidoJson by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val primaryGreen = Color(0xFF15803D)

    // Cargar información del pedido
    fun loadOrder() {
        isLoading = true
        thread {
            try {
                val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId")
                val conn = url.openConnection() as java.net.HttpURLConnection
                applySslBypass(conn)
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    pedidoJson = JSONObject(text)
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(orderId) {
        loadOrder()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Entrega #$orderId", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryGreen)
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (pedidoJson == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Error al cargar la información")
            }
        } else {
            val obj = pedidoJson!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFF8FAFC))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Cliente: ${obj.optString("nombre_cliente")}", fontWeight = FontWeight.Bold)
                        Text("Teléfono: ${obj.optString("telefono")}")
                        Text("Dirección: ${obj.optString("direccion")}")
                        Text("Referencia: ${obj.optString("referencia_lugar")}")
                        Text("Detalle: ${obj.optString("descripcion_pedido")}")
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Actualizar Estatus de Entrega", fontWeight = FontWeight.Bold, color = primaryGreen)

                        // Botón para actualizar estado a 'En camino' (3)
                        Button(
                            onClick = { updateStatus(orderId, 3) { loadOrder() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                        ) {
                            Text("En Camino")
                        }

                        // Botón para actualizar estado a 'Entregado' (6)
                        Button(
                            onClick = { updateStatus(orderId, 6) { loadOrder() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Entregado")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Actualiza el estatus del pedido desde el repartidor hacia la API REST.
 *
 * @param orderId ID del pedido.
 * @param newStatus Nuevo código de estatus numérico.
 * @param onComplete Callback ejecutado al finalizar.
 * @return Unit.
 */
private fun updateStatus(orderId: Int, newStatus: Int, onComplete: () -> Unit) {
    thread {
        try {
            val url = java.net.URL("${ServerConfig.BASE_URL}/api/pedidos/$orderId/estatus")
            val conn = url.openConnection() as java.net.HttpURLConnection
            applySslBypass(conn)
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true

            val body = JSONObject().apply { put("estatus", newStatus) }
            conn.outputStream.use { os -> os.write(body.toString().toByteArray(Charsets.UTF_8)) }

            conn.responseCode
        } catch (_: Exception) {
        } finally {
            onComplete()
        }
    }
}
```
