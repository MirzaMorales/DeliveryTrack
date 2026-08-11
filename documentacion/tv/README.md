# Documentación — Módulo TV (Leanback Dashboard)

El módulo `tv` es una aplicación de Android TV diseñada para visualizar el panel de control logístico en tiempo real. Utiliza **Jetpack Compose** para construir interfaces dinámicas adaptadas a pantallas grandes (Smart TV) y se integra con **Google Maps** y **WebSockets** para ofrecer telemetría en tiempo real sobre los repartidores y pedidos del sistema.

---

## 🛠️ Configuración del Módulo (Gradle)

### [`build.gradle.kts`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/build.gradle.kts)
Archivo de configuración de Gradle que define el build pipeline y las dependencias esenciales para correr la app en Android TV con Jetpack Compose y mapas interactivos:
- **Plugins**: Carga el plugin de aplicación de Android, soporte para Kotlin Android y el plugin del compilador de Compose.
- **Configuración de SDK**: Compila y apunta al SDK versión 36 (`compileSdk`/`targetSdk`), con un mínimo requerido de SDK 26 (`minSdk`).
- **Dependencias**:
  - `project(":shared")`: Vincula el módulo compartido para reutilizar la configuración del servidor y los modelos.
  - `platform(libs.compose.bom)` y librerías de Compose (Activity, UI, Graphics, Foundation, Material3): Para el desarrollo moderno de interfaces declarativas.
  - `com.squareup.okhttp3:okhttp`: Cliente HTTP para crear el WebSocket.
  - `com.google.android.gms:play-services-maps`: Biblioteca oficial de Google Play Services para cargar mapas interactivos en la TV.

---

## 🗺️ Manifiesto de la Aplicación

### [`AndroidManifest.xml`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/src/main/AndroidManifest.xml)
Configuración principal de metadatos y permisos del módulo Android TV:
- **Permisos**: Declara la necesidad de acceso a `android.permission.INTERNET` para conectar con el backend remoto.
- **Características de TV**: Declara la característica de hardware `android.hardware.type.television` con `android:required="false"` para permitir compatibilidad Leanback.
- **Configuración de Google Maps**: Define la etiqueta `<meta-data>` con la clave `com.google.android.geo.API_KEY` para autenticar y cargar el mapa de Google.
- **Actividad Principal**: Registra `TvDashboardActivity` como la actividad de lanzamiento usando la categoría `android.intent.category.LEANBACK_LAUNCHER`.

---

## 📦 Capa de Datos y Red (Data & Network)

### [`mqtt/TvWebSocketClient.kt`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/src/main/java/mx/utng/deliverytrack/tv/mqtt/TvWebSocketClient.kt)
Componente central encargado de mantener la comunicación bidireccional en tiempo real con el servidor:
- **Conectividad**: Utiliza `OkHttpClient` de OkHttp para iniciar una conexión a la URL del WebSocket del servidor (`wss://.../ws`).
- **Mecanismo de Reconexión (Exponential Backoff)**: En caso de desconexión o fallo (`onFailure`/`onClosed`), inicia un temporizador de reconexión que incrementa exponencialmente el tiempo de espera (1s, 2s, 4s...) hasta un máximo de 30 segundos, evitando saturar el servidor.
- **Estado de Conexión**: Publica el estado actual de la conexión mediante un `MutableStateFlow` (`connectionState: StateFlow<Boolean>`).
- **Procesamiento de Mensajes**:
  - Al abrirse la conexión, recibe un mensaje `snapshot` con todos los datos iniciales del sistema (KPIs, lista de pedidos activos y ubicaciones de repartidores).
  - Procesa mensajes en tiempo real como `pedido_creado`, `pedido_actualizado` y `ubicacion_actualizada` para refrescar los flujos locales de datos en Compose de forma reactiva.

### [`data/TvRepository.kt`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/src/main/java/mx/utng/deliverytrack/tv/data/TvRepository.kt)
Clase repositorio intermedia que hace uso de `RemoteDataSource` para realizar llamadas tradicionales de tipo GET a la API REST (en este caso, al endpoint `/api/dashboard/metrics`), pensada para consultas de diagnóstico o sincronización clásica de métricas del panel.

---

## 📐 Capa de Dominio (Modelos de Datos)

### [`domain/model/DashboardMetrics.kt`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/src/main/java/mx/utng/deliverytrack/tv/domain/model/DashboardMetrics.kt)
Define la estructura básica de datos para almacenar las métricas globales del tablero logístico como:
- `pedidosActivos`: Número de pedidos pendientes de entrega.
- `entregadosHoy`: Cantidad de entregas exitosas del día corriente.
- `tiempoPromedioMin`: Tiempo promedio de entrega medido en minutos.
- `incidencias`: Total de pedidos con estatus de retrasado.
- `repartidoresEnRuta`: Cantidad de repartidores activos actualmente entregando pedidos.

### [`domain/model/DashboardModels.kt`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/src/main/java/mx/utng/deliverytrack/tv/domain/model/DashboardModels.kt)
Contiene las representaciones y DTOs (Data Transfer Objects) que se reciben del backend a través de la comunicación WebSocket:
- `PedidoDto`: Datos del pedido (cliente, teléfono, dirección, referencias, descripción, estatus y repartidor asignado).
- `RepartidorUbicacionDto`: Datos de telemetría (ID del repartidor, coordenadas de latitud y longitud, velocidad registrada y porcentaje de batería del móvil).
- `KpisDto`: Estructura para parsear las métricas dentro del mensaje de inicialización.
- `WsEvent`: Clase sellada (`sealed class`) que modela los diferentes tipos de eventos del WebSocket para un control de flujo estructurado (`Snapshot`, `PedidoCreado`, `PedidoActualizado`, `UbicacionActualizada`).

---

## 🎨 Capa de Presentación (UI)

### [`presentation/TvDashboardActivity.kt`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/src/main/java/mx/utng/deliverytrack/tv/presentation/TvDashboardActivity.kt)
Actividad principal y corazón visual de la aplicación para Smart TV:
- **Flujo de Compose**: Invoca a `TvDashboardScreen` pasando una instancia de `TvWebSocketClient`.
- **Colección de Estados**: Utiliza `collectAsState()` para observar reactivamente `kpis`, `connectionState`, `pedidos` y `repartidores`.
- **Estructura del Panel**:
  - **Cabecera**: Muestra el nombre de la plataforma y un indicador dinámico visual del estado de la conexión en vivo ("● En vivo" en verde o "○ Desconectado" en rojo).
  - **Tarjetas de Métricas**: Cinco tarjetas superiores que muestran los KPIs actualizados al instante con colores distintivos.
  - **Lista de Pedidos Activos**: Columna izquierda que enlista las órdenes activas mostrando el nombre del cliente, dirección, descripción e indicador de estatus coloreado (Aceptado, Pendiente, En camino, Retrasado).
  - **Mapa Logístico**: Integración en el lado derecho para ver el mapa de Google Maps con las posiciones en vivo.

### [`presentation/components/FleetMap.kt`](file:///c:/Users/natzl/AndroidStudioProjects/DeliveryTrack/tv/src/main/java/mx/utng/deliverytrack/tv/presentation/components/FleetMap.kt)
Componente encargado de pintar el mapa logístico interactivo:
- **Google Maps integration**: Utiliza el contenedor Compose de Google Maps.
- **Gestión de Marcadores**:
  - Dibuja un pin por cada repartidor activo en el sistema, mostrando su nombre y porcentaje de batería directamente sobre el mapa.
  - Actualiza automáticamente la posición de los pines sin parpadeos cada vez que entra un evento de telemetría de coordenadas GPS.
  - Mantiene un centrado adaptativo para dar visibilidad de la flota de reparto.
