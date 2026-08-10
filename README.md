# DeliveryTrack

Plataforma de telemetría y gestión de entregas en tiempo real para repartidores, compuesta por un **wearable (WearOS)**, una **app móvil (Android)** y un **backend** centralizado, sincronizados mediante la Wearable Data Layer API y una API REST.

## Datos del proyecto

- **Nombre del proyecto:** DeliveryTrack
- **Estudiantes:**
  - Carmen Catalina Delgado Manzano
  - Mirza Natzielly Morales Lezama
  - Gael Quintana Romero
- **Grupo:** GIDS6093
- **Institución:** Universidad Tecnológica del Norte de Guanajuato (UTNG)

---

## Beneficiario

- **Nombre:** Sandra Elizabeth Delgado Gutierrez
- **Negocio:** Rosticería "Lindavista"
- **Puesto:** Propietaria
- **Dirección:** Miguel Hidalgo #2, Local 3

DeliveryTrack está pensado para negocios de comida como la **Rosticería "Lindavista"**, que realizan entregas a domicilio con repartidores propios. Al centralizar la información en el reloj inteligente, el repartidor puede aceptar, iniciar y confirmar entregas con un solo vistazo y un toque, sin distraerse revisando el teléfono, mientras la propietaria obtiene trazabilidad en tiempo real del estatus de cada pedido (aceptado, en ruta, entregado, cancelado o retrasado) para mejorar la operación del negocio y la experiencia de sus clientes.

---

## Diagrama de arquitectura

El siguiente diagrama resume el flujo de datos entre el reloj (sensor/wearable), el teléfono (puente de comunicación) y el backend, que persiste la información en PostgreSQL:

<img width="1267" height="672" alt="image" src="https://github.com/user-attachments/assets/d217677c-c5e8-4641-a2fd-c6e316d3bfce" />


**Flujo resumido:**
1. El **reloj (WearOS)** carga el pedido activo del repartidor y permite actualizar su estatus (Aceptar → En camino → Entregado).
2. El **teléfono (Mobile Hub)** actúa como puente vía **Wearable Data Layer API**, reenviando eventos (nuevo pedido, cancelación) al reloj con alertas hápticas, y consumiendo la **API REST** del backend por HTTP (OkHttp).
3. El **backend** (Node.js + Express + TypeScript) expone los endpoints REST y persiste usuarios, pedidos, ubicaciones GPS e historial de estatus en **PostgreSQL**.

---

## Objetivo

Desarrollar una plataforma multi-dispositivo que permita a un repartidor gestionar sus pedidos activos directamente desde su reloj inteligente (WearOS), mientras un dispositivo móvil actúa como puente de comunicación (Mobile Hub) entre el reloj y un backend centralizado, manteniendo sincronizado en tiempo real el estatus de cada entrega y notificando al repartidor mediante alertas hápticas ante eventos relevantes (nuevo pedido asignado, cancelación, etc.).

---

## Descripción de las funcionalidades

### Wearable (WearOS)
- Carga automáticamente el pedido activo asignado al repartidor.
- Muestra número de pedido, nombre del cliente, dirección/referencia y descripción del pedido.
- Flujo de botones para actualizar el estatus del pedido en tiempo real:
  - **Aceptar** → cambia el estatus a *Aceptado*.
  - **En camino** → cambia el estatus a *En ruta*.
  - **Entregado** → cambia el estatus a *Entregado* y limpia la pantalla ("Sin entregas activas").
- Recibe alertas hápticas (vibración) cuando el teléfono notifica un nuevo pedido o una cancelación, y refresca la información automáticamente.

### App móvil (Mobile Hub - Android)
- Actúa como puente de comunicación entre el reloj y el backend mediante la **Wearable Data Layer API**.
- Permite **simular eventos de prueba**:
  - *Simulate New Order Assigned*: envía al reloj una alerta de nuevo pedido (vibración doble).
  - *Simulate Active Order Canceled*: envía al reloj una alerta de cancelación (vibración triple).
- Consume la API REST del backend vía HTTP (OkHttp).

### Backend (API REST)
- Expone endpoints para consultar el pedido activo de un repartidor y actualizar su estatus.
- Persiste usuarios, pedidos, ubicaciones GPS e historial de estatus en PostgreSQL.
- Incluye script de inicialización de base de datos con datos de prueba (un repartidor y un pedido pendiente).

### Smart TV (TV Dashboard)
- Panel logístico centralizado diseñado para pantallas grandes (Smart TV / Leanback).
- Conexión persistente mediante **WebSockets** para recibir actualizaciones en tiempo real sobre la ubicación de los repartidores y cambios en los pedidos sin recargar la página.
- Visualización de indicadores clave (KPIs) en tiempo real: Pedidos activos, Entregados hoy, Tiempo promedio de entrega, Incidencias y Repartidores en ruta.
- Mapa dinámico (Google Maps) que muestra la telemetría en tiempo real y la ubicación de los repartidores activos.

### Módulo Shared
- Biblioteca compartida reutilizable entre los módulos `mobile`, `wear` y `tv`.
- Contiene configuraciones centralizadas del servidor (`ServerConfig`) como las URLs del backend y WebSocket (`BASE_URL` y `WS_URL`).
- Reduce la duplicación de código compartiendo clases de datos comunes o utilidades.

---

## Tecnologías utilizadas

| Capa | Tecnología |
|------|-----------|
| Wearable | Kotlin + Jetpack Compose (Material3 for Wear) + WearOS |
| Mobile Hub | Kotlin + Jetpack Compose + Android |
| Comunicación reloj ↔ teléfono | Wearable Data Layer API (BLE) |
| Comunicación teléfono ↔ backend | OkHttp (HTTP REST) |
| Backend | Node.js + Express + TypeScript |
| Base de datos | PostgreSQL 16 (Docker) |
| Build system | Gradle (Kotlin DSL) |

---

## Instrucciones para ejecutar el proyecto

### Requisitos previos

- [Node.js](https://nodejs.org/) v18 o superior
- [Docker](https://www.docker.com/) (para PostgreSQL)
- [Android Studio](https://developer.android.com/studio) Hedgehog o superior
- Emulador de teléfono (Pixel 7, API 34) y emulador WearOS pareados en Android Studio

### 1. Base de datos (PostgreSQL en Docker)

Crear el contenedor (solo la primera vez):

```bash
docker run --name deliverytrack-db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=deliverytrack \
  -p 5432:5432 \
  -d postgres
```

Iniciar el contenedor (usos posteriores):

```bash
docker start deliverytrack-db
```

Verificar que el contenedor está corriendo:

```bash
docker ps
```

### 2. Backend

Instalar dependencias:

```bash
npm install --prefix backend
```

Inicializar esquema y datos semilla:

```bash
npm run init-db --prefix backend
```

Esto crea las tablas `usuario`, `pedido`, `ubicaciongps` e `historialestatus`, e inserta un repartidor de prueba (**Carlos**, ID 2) con un pedido pendiente asignado (**Juan Pérez**, ID 1).

Iniciar el servidor:

```bash
npm run dev --prefix backend
```

El servidor corre en `http://localhost:3000`. En consola se debe ver:
[server]: Server is running at http://localhost:3000

### 3. Aplicación Android

1. Abrir el proyecto en **Android Studio**.
2. En la barra superior, seleccionar el módulo **`mobile`** y el emulador de teléfono → **Run**.
3. Cambiar al módulo **`app`** y el emulador WearOS → **Run**.

> Los dos emuladores deben estar pareados. Al crear el emulador WearOS en Device Manager, seleccionar el teléfono en la opción **Companion Phone**.

### 4. Flujo de prueba

Con el backend corriendo y ambos emuladores activos:

1. La app del **reloj** carga automáticamente el pedido activo del repartidor ID 2.
2. Se muestra: número de pedido, nombre del cliente y dirección.
3. Flujo de botones:
   - **Aceptar** → estatus cambia a `1` en base de datos.
   - **En camino** → estatus cambia a `3`.
   - **Entregado** → estatus cambia a `6`, pantalla muestra "Sin entregas activas".

Para probar las alertas hápticas, desde el emulador del **teléfono** (app Mobile Hub abierta):

- **Simulate New Order Assigned** → el reloj vibra con doble pulso y refresca el pedido.
- **Simulate Active Order Canceled** → el reloj vibra con triple pulso y limpia la pantalla.

### 5. Resetear pedido de prueba

Si el pedido ya fue marcado como Entregado (estatus 6) y se quiere repetir el flujo completo:

```bash
docker exec -it deliverytrack-db psql -U postgres -d deliverytrack \
  -c "UPDATE pedido SET estatus = 2 WHERE id_pedido = 1;"
```

---

## Endpoints disponibles

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/health` | Health check del servidor |
| `GET` | `/api/pedidos/activo?repartidorId={id}` | Obtiene el pedido activo del repartidor |
| `PATCH` | `/api/pedidos/:id/estatus` | Actualiza el estatus de un pedido |

### Valores de estatus

| Código | Descripción |
|--------|-------------|
| 1 | Aceptado |
| 2 | Pendiente |
| 3 | En ruta |
| 4 | Cancelado |
| 5 | Retrasado |
| 6 | Entregado |

---

## Capturas de pantalla

### Mobile Repartidor
#### Inicio de Sesión
<img width="367" height="827" alt="image" src="https://github.com/user-attachments/assets/7e0bdd91-73ad-4f65-9808-e1f7768e1ce1" />

#### Dashboard
<img width="365" height="822" alt="image" src="https://github.com/user-attachments/assets/6e278e26-f977-468a-9636-f322112ec738" />

#### Mi perfil
<img width="365" height="831" alt="image" src="https://github.com/user-attachments/assets/3039646b-04bd-4133-aab9-5a70952d87ea" />

#### Ver detalle pedido
<img width="377" height="767" alt="image" src="https://github.com/user-attachments/assets/7fa5ec8d-ba37-4ae1-ae4a-930f8b657915" />

#### Iniciar Navegación
<img width="370" height="822" alt="image" src="https://github.com/user-attachments/assets/ff5d3b8f-3641-433a-a9b2-3ed2ed1b5935" />

#### Filtros
<img width="366" height="826" alt="Cancelados" src="https://github.com/user-attachments/assets/7b94090f-78bc-4ba8-92c5-80b2c9df7b06" />
<img width="367" height="795" alt="Aceptados" src="https://github.com/user-attachments/assets/4c1dc3fd-9bf2-454c-9c41-108bf5414e2a" />
<img width="377" height="835" alt="En camino" src="https://github.com/user-attachments/assets/6f05ff32-bb4f-4f37-87f8-2eabf1a3d144" />
<img width="368" height="827" alt="Entregados" src="https://github.com/user-attachments/assets/eebef051-8b26-4f2c-b211-a5750de271ef" />
<img width="361" height="832" alt="Pendiendes" src="https://github.com/user-attachments/assets/3d7305eb-ee93-413d-a22c-4aee0899b30b" />


### Mobile Admin
#### Inicio de Sesión
<img width="367" height="827" alt="image" src="https://github.com/user-attachments/assets/7e0bdd91-73ad-4f65-9808-e1f7768e1ce1" />

#### Dashboard
<img width="365" height="827" alt="Dashboard" src="https://github.com/user-attachments/assets/1591024f-d297-48d4-a0f4-879aec2a8fc8" />

#### Gestión de Usuarios
<img width="360" height="832" alt="Gestion Usuarios" src="https://github.com/user-attachments/assets/6049d77d-efef-408d-b106-b27b06a6c5c2" />
<img width="368" height="828" alt="Modificar Usuario" src="https://github.com/user-attachments/assets/25ea53c3-2c87-4884-a4af-0bfe95c8b479" />
<img width="368" height="828" alt="Modificar Usuario" src="https://github.com/user-attachments/assets/401d6204-1b5b-49e5-8add-272cf66e714f" />
<img width="371" height="827" alt="Suspender Usuario" src="https://github.com/user-attachments/assets/22e8e846-8d62-4574-a16e-221f259e33be" />

#### Creación de Pedidos
<img width="370" height="806" alt="Nuevo Pedido" src="https://github.com/user-attachments/assets/e21210f8-4699-440e-95fd-223a45d7a67a" />

#### Detalle de Pedidos
<img width="381" height="821" alt="Detalles Pedidos" src="https://github.com/user-attachments/assets/7a3addb4-e943-4479-b8f9-e81bc5ae1951" />

#### Editar Pedido
<img width="386" height="822" alt="Editar Pedido" src="https://github.com/user-attachments/assets/2599e886-b513-45f7-afb8-27178004210e" />

#### Cancelar Pedido
<img width="365" height="801" alt="image" src="https://github.com/user-attachments/assets/9d11fc77-a1cb-4aad-8c84-0112c2779d33" />

#### Rastreo en Mapa
<img width="362" height="806" alt="image" src="https://github.com/user-attachments/assets/084de811-2d5f-4021-86d7-2b9ea8b6744b" />

#### Filtros
<img width="381" height="828" alt="Filtro por Repartidor" src="https://github.com/user-attachments/assets/0357c950-07ac-4093-914b-c99ef8a9ce3c" />
<img width="366" height="822" alt="Filtro por Status" src="https://github.com/user-attachments/assets/12a6ada5-165b-4ad7-8404-eef1416d559d" />


### Wearable
#### Inicio de Sesión
<img width="477" height="483" alt="inicio sesion" src="https://github.com/user-attachments/assets/5628f8a7-1bdc-49d2-9cb9-d26d834b4072" />

#### Dashboard
<img width="532" height="483" alt="dashboard" src="https://github.com/user-attachments/assets/6f065335-b882-4ef7-b769-215823eda0cf" />

#### Pendiente
<img width="571" height="557" alt="Pendiente" src="https://github.com/user-attachments/assets/5b9edb91-8225-48ef-a7ce-feb8029ce9f2" />

#### Despachar Pedido
<img width="553" height="498" alt="image" src="https://github.com/user-attachments/assets/95697ae1-8df8-48b3-8f5e-a762eb18918e" />

#### Tras aceptar
<img width="542" height="543" alt="En camino tras aceptar" src="https://github.com/user-attachments/assets/c26e895f-fab8-470d-a1be-2d4ce338826f" />

#### En camino
<img width="531" height="528" alt="en camino" src="https://github.com/user-attachments/assets/90b1d1a0-8fa9-4cda-b8c4-49bdae71ea0e" />

#### Entregado
<img width="522" height="560" alt="Entrega Completada" src="https://github.com/user-attachments/assets/5d42a259-2bd1-4c3f-be06-25cb16838dd8" />

#### Cancelado
<img width="570" height="512" alt="Cancelado" src="https://github.com/user-attachments/assets/be32befe-9bce-466d-8689-d85f2cde437a" />


### TV 
#### Estado inicial
<img width="981" height="560" alt="Dashboard" src="https://github.com/user-attachments/assets/7e1afcab-2f88-46e1-8790-0786a2c8efcb" />

#### Entrega Pendiente
<img width="1130" height="666" alt="Pendiente" src="https://github.com/user-attachments/assets/48e26324-ae06-4c76-9982-d8be8140298f" />

#### Aceptado
<img width="1077" height="628" alt="image" src="https://github.com/user-attachments/assets/1129655d-d924-44f4-826e-f56f7425f3c7" />

#### En camino
<img width="1093" height="627" alt="EN CAMINO" src="https://github.com/user-attachments/assets/5433a6b3-04b1-4644-8272-87b9b648afdd" />

#### Entregado
<img width="1103" height="652" alt="Entregado" src="https://github.com/user-attachments/assets/daaa188b-7c94-477c-80d9-b0675ed5f351" />
