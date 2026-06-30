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

> Agrega aquí las capturas de pantalla de la app del reloj (WearOS), la app móvil (Mobile Hub) y el backend en ejecución.

| Wearable (WearOS) | App móvil (Mobile Hub) |
|---|---|
| ![Pantalla del reloj](docs/screenshots/wear-pedido.png) | ![Pantalla del móvil](docs/screenshots/mobile-hub.png) |
