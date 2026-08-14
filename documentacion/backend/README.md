# Documentación Completa del Módulo Backend

El módulo `backend` es el servidor API REST y WebSockets de la plataforma DeliveryTrack. Desarrollado con **Node.js**, **Express**, **TypeScript** y **PostgreSQL** (compatible con servidores locales y bases de datos serverless en **Neon.tech**).

Su función principal es gestionar la persistencia central del sistema: autenticación de usuarios con cifrado seguro de contraseñas (Bcrypt), gestión del ciclo de vida de pedidos, procesamiento de telemetría de ubicación GPS, auditoría de estados y transmisión en tiempo real a clientes conectados (Dashboard TV y Apps Móviles/Wearable) mediante WebSockets.

A continuación, se detalla la estructura organizada por carpetas, con la explicación completa y el código fuente comentado de cada archivo del backend.

---

## 1. Configuración del Proyecto y Dependencias (`backend/`)

Esta sección abarca los archivos de configuración raíz del proyecto backend en Node.js, donde se establecen las dependencias necesarias, scripts de desarrollo/producción, variables de entorno y las opciones de compilación de TypeScript.

---

### `backend/package.json`
**Funcionalidad:** Archivo de manifiesto de Node.js. Define el nombre del paquete `deliverytrack-backend`, versión, comandos de ejecución (`dev` con `ts-node-dev`, `build` con `tsc`, `start` y `init-db`), y declara las dependencias principales (`express`, `pg`, `ws`, `bcryptjs`, `cors`, `dotenv`) y secundarias (tipos TypeScript).

```json
{
  "name": "deliverytrack-backend",
  "version": "1.0.0",
  "description": "Backend para la plataforma de logística y telemetría DeliveryTrack",
  "main": "dist/index.js",
  "scripts": {
    "build": "tsc",
    "start": "node dist/index.js",
    "dev": "ts-node-dev --respawn --transpile-only src/index.ts",
    "init-db": "ts-node-dev src/initDb.ts"
  },
  "keywords": [],
  "author": "",
  "license": "ISC",
  "dependencies": {
    "@types/bcryptjs": "^2.4.6",
    "bcryptjs": "^3.0.3",
    "cors": "^2.8.5",
    "dotenv": "^16.4.5",
    "express": "^4.19.2",
    "pg": "^8.12.0",
    "ws": "^8.21.1"
  },
  "devDependencies": {
    "@types/cors": "^2.8.17",
    "@types/express": "^4.17.21",
    "@types/node": "^20.14.9",
    "@types/pg": "^8.11.6",
    "@types/ws": "^8.18.1",
    "rimraf": "^5.0.7",
    "ts-node-dev": "^2.0.0",
    "typescript": "^5.5.2"
  }
}
```

---

### `backend/tsconfig.json`
**Funcionalidad:** Archivo de configuración del compilador de TypeScript (`tsc`). Define la versión objetivo del ECMAScript (`es2022`), el sistema de módulos CommonJS, los directorios de origen `./src` y de salida compilada `./dist`, e incluye comprobaciones estrictas de tipos (`strict: true`).

```json
{
  "compilerOptions": {
    "target": "es2022",
    "module": "commonjs",
    "moduleResolution": "node",
    "rootDir": "./src",
    "outDir": "./dist",
    "esModuleInterop": true,
    "forceConsistentCasingInFileNames": true,
    "strict": true,
    "skipLibCheck": true
  },
  "include": ["src/**/*"]
}
```

---

### `backend/.env.example`
**Funcionalidad:** Plantilla de variables de entorno para la configuración de despliegue y desarrollo local. Especifica el puerto de escucha HTTP (puerto 3000 por defecto) y la cadena de conexión cifrada PostgreSQL hacia la base de datos remota Neon.tech.

```bash
PORT=3000

# Cadena de conexión para PostgreSQL en Neon.tech (Reemplazar con credenciales reales)
NEON_CONN_STRING=postgresql://neondb_owner:pass.neon.tech/neondb?sslmode=require&channel_binding=require
```

---

## 2. Base de Datos - Esquema SQL (`backend/`)

Esta sección define el esquema relacional de la base de datos PostgreSQL y los datos semilla (seed data) iniciales para pruebas.

---

### `backend/schema.sql`
**Funcionalidad:** Script SQL de creación de tablas e inserción de datos iniciales. Crea las tablas relacionales principales: `usuario`, `pedido`, `ubicaciongps` e `historialestatus`, con claves primarias auto-incrementables (`SERIAL`), llaves foráneas (`REFERENCES`), restricciones y valores por defecto. Ajusta las secuencias al finalizar las inserciones para evitar colisiones de IDs.

```sql
-- Esquema de Base de Datos para DeliveryTrack

-- 1. Tabla de Usuarios del Sistema (Administradores y Repartidores)
CREATE TABLE IF NOT EXISTS usuario (
    id_user SERIAL PRIMARY KEY,
    nombre_completo VARCHAR(255) NOT NULL,
    telefono VARCHAR(20),
    contrasena VARCHAR(255) NOT NULL,
    rol INT NOT NULL, -- 1 = Admin, 2 = Repartidor
    estatus INT NOT NULL DEFAULT 1 -- 1 = Activo, 2 = Inactivo, 3 = Suspensión
);

-- 2. Tabla de Pedidos / Ordenes de Entrega
CREATE TABLE IF NOT EXISTS pedido (
    id_pedido SERIAL PRIMARY KEY,
    nombre_cliente VARCHAR(255) NOT NULL,
    telefono VARCHAR(20),
    direccion VARCHAR(255) NOT NULL,
    referencia_lugar VARCHAR(255),
    descripcion_pedido TEXT,
    fecha DATE DEFAULT CURRENT_DATE,
    hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estatus INT NOT NULL DEFAULT 2, -- 1 = Aceptado, 2 = Pendiente, 3 = En ruta, 4 = Cancelado, 5 = Retrasado, 6 = Entregado
    comentario TEXT,
    id_repartidor INT REFERENCES usuario(id_user) ON DELETE SET NULL
);

-- 3. Tabla de Registro de Telemetría y Coordenadas GPS
CREATE TABLE IF NOT EXISTS ubicaciongps (
    id_ubicacion SERIAL PRIMARY KEY,
    latitud DECIMAL(10,8) NOT NULL,
    longitud DECIMAL(11,8) NOT NULL,
    velocidad DECIMAL(5,2),
    precision_gps DECIMAL(5,2),
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    bateria SMALLINT,
    señal_gps INT, -- 1 = Buena, 2 = Débil, 3 = Sin señal
    id_repartidor INT NOT NULL REFERENCES usuario(id_user) ON DELETE CASCADE
);

-- 4. Tabla de Auditoría Histórica de Estados de Pedidos
CREATE TABLE IF NOT EXISTS historialestatus (
    id_historial SERIAL PRIMARY KEY,
    estatus VARCHAR(50) NOT NULL, -- Ej. "Aceptado", "Pendiente", "En ruta", "Entregado", etc.
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    id_usuario INT NOT NULL REFERENCES usuario(id_user) ON DELETE CASCADE,
    id_pedido INT NOT NULL REFERENCES pedido(id_pedido) ON DELETE CASCADE
);

-- Inserción de Datos Semilla Iniciales con IDs Explícitos
INSERT INTO usuario (id_user, nombre_completo, telefono, contrasena, rol, estatus)
VALUES (1, 'Ana Administradora', '5557654321', 'admin123', 1, 1)
ON CONFLICT DO NOTHING;

INSERT INTO usuario (id_user, nombre_completo, telefono, contrasena, rol, estatus)
VALUES (2, 'Carlos Repartidor', '5551234567', 'password123', 2, 1)
ON CONFLICT DO NOTHING;

-- Inserción de Pedido Activo de Prueba
INSERT INTO pedido (id_pedido, nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, estatus, id_repartidor)
VALUES (
    1,
    'Juan Pérez', 
    '5559876543', 
    'Av. Juárez 123, Col. Centro', 
    'Frente a la farmacia Guadalajara', 
    '1 Pizza Familiar Pepperoni y 1 Refresco de 2L', 
    2, -- Pendiente (2)
    2  -- Asignado a Carlos Repartidor (id_user = 2)
)
ON CONFLICT DO NOTHING;

-- Actualización de Secuencias de IDs Auto-incrementables
SELECT setval(pg_get_serial_sequence('usuario', 'id_user'), coalesce(max(id_user), 1)) FROM usuario;
SELECT setval(pg_get_serial_sequence('pedido', 'id_pedido'), coalesce(max(id_pedido), 1)) FROM pedido;
```

---

## 3. Capa de Conexión a Base de Datos (`src/`)

Esta sección abarca las clases encargadas de establecer la conexión con el motor PostgreSQL utilizando un pool de conexiones reutilizables.

---

### `backend/src/db.ts`
**Funcionalidad:** Módulo de conexión a la base de datos PostgreSQL. Utiliza la librería `pg` y gestiona un pool de conexiones (`Pool`). Detecta si se dispone de una cadena de conexión cifrada SSL (para **Neon.tech**) en las variables de entorno `.env` o en el archivo `local.properties` del espacio de trabajo. Exporta la función helper `query(text, params)` para ejecutar consultas SQL parametrizadas de forma segura evitando inyecciones SQL.

```typescript
import { Pool } from 'pg';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';

// Cargar variables de entorno desde el archivo .env
dotenv.config();

// Intentar leer local.properties desde la raíz del workspace si la variable de entorno no está definida
const rootLocalPropsPath = path.resolve(__dirname, '../../local.properties');
let neonConnectionString = process.env.NEON_CONN_STRING || process.env.DATABASE_URL;

if (!neonConnectionString && fs.existsSync(rootLocalPropsPath)) {
  const content = fs.readFileSync(rootLocalPropsPath, 'utf-8');
  const match = content.match(/NEON_CONN_STRING=(.+)/);
  if (match && match[1]) {
    neonConnectionString = match[1].trim();
  }
}

// Configuración de la conexión PostgreSQL (Neon.tech SSL o PostgreSQL Local)
const poolConfig = neonConnectionString
  ? {
      connectionString: neonConnectionString,
      ssl: {
        rejectUnauthorized: false
      }
    }
  : {
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '5432'),
      user: process.env.DB_USER || 'postgres',
      password: process.env.DB_PASSWORD || 'postgres',
      database: process.env.DB_DATABASE || 'deliverytrack',
    };

// Instancia global del pool de conexiones PostgreSQL
const pool = new Pool(poolConfig);

/**
 * Función helper para ejecutar consultas SQL parametrizadas.
 * 
 * @param text Sentencia SQL parametrizada con $1, $2, etc.
 * @param params Arreglo de valores para sustituir en los parámetros.
 */
export const query = (text: string, params?: any[]) => {
  return pool.query(text, params);
};

export default pool;
```

---

## 4. Scripts de Inicialización y Semillero (`src/`)

Esta sección abarca los scripts ejecutables para inicializar la estructura de la base de datos e insertar cuentas administrativas con contraseñas cifradas.

---

### `backend/src/initDb.ts`
**Funcionalidad:** Script de inicialización de la base de datos. Lee el archivo SQL `schema.sql` e ejecuta su contenido de forma asíncrona mediante el pool de conexiones, creando todas las tablas relacionales y registros semilla iniciales.

```typescript
import fs from 'fs';
import path from 'path';
import { query } from './db';

/**
 * Inicializa las tablas de la base de datos PostgreSQL a partir de schema.sql.
 */
const initDb = async () => {
  console.log('Inicializando la base de datos...');
  try {
    const schemaPath = path.join(__dirname, '../schema.sql');
    const sql = fs.readFileSync(schemaPath, 'utf8');

    console.log('Ejecutando script de esquema SQL...');
    await query(sql);

    console.log('¡Base de datos inicializada exitosamente con el esquema y datos semilla!');
    process.exit(0);
  } catch (error) {
    console.error('Error al inicializar la base de datos:', error);
    process.exit(1);
  }
};

initDb();
```

---

### `backend/src/createAdmin.ts`
**Funcionalidad:** Script especializado para sembrar/actualizar la cuenta del Administrador General en la base de datos de Neon.tech. Aplica un hash seguro de la contraseña mediante **Bcrypt** (`bcrypt.hashSync('admin123', 10)`), garantizando un acceso seguro desde la app móvil.

```typescript
import bcrypt from 'bcryptjs';
import { query } from './db';

/**
 * Crea o actualiza la cuenta de usuario Administrador General en la base de datos.
 */
const createAdmin = async () => {
  console.log('--- Creando Usuario Administrador en Neon.tech PostgreSQL ---');
  try {
    const adminPhone = '5550000000';
    const plainPassword = 'admin123';
    
    // Generar Hash Bcrypt seguro con factor de costo 10
    const hashedPassword = bcrypt.hashSync(plainPassword, 10);

    // Verificar si el usuario ya existe
    const existing = await query('SELECT * FROM usuario WHERE telefono = $1', [adminPhone]);

    if (existing.rows.length > 0) {
      console.log('El usuario administrador ya existe. Actualizando hash de contraseña...');
      await query(
        `UPDATE usuario 
         SET contrasena = $1, rol = 1, estatus = 1, nombre_completo = 'Admin General' 
         WHERE telefono = $2`,
        [hashedPassword, adminPhone]
      );
    } else {
      console.log('Insertando nuevo usuario Administrador...');
      await query(
        `INSERT INTO usuario (nombre_completo, telefono, contrasena, rol, estatus)
         VALUES ('Admin General', $1, $2, 1, 1)`,
        [adminPhone, hashedPassword]
      );
    }

    console.log('¡Administrador creado exitosamente!');
    console.log(`📱 Teléfono Admin: ${adminPhone}`);
    console.log(`🔑 Contraseña Llana: ${plainPassword}`);
    console.log(`🔒 Contraseña Hash en Base de Datos: ${hashedPassword}`);

    // Mostrar tabla de usuarios actuales en consola
    const allUsers = await query('SELECT id_user, nombre_completo, telefono, contrasena, rol, estatus FROM usuario ORDER BY id_user ASC');
    console.table(allUsers.rows);

    process.exit(0);
  } catch (error) {
    console.error('Error al crear usuario administrador:', error);
    process.exit(1);
  }
};

createAdmin();
```

---

## 5. Servidor Express y WebSockets (`src/`)

Esta sección abarca el núcleo principal del backend: el servidor HTTP REST Express y el servidor WebSockets para comunicación en tiempo real.

---

### `backend/src/index.ts`
**Funcionalidad:** Archivo central del servidor backend. Inicia la aplicación Express, configura CORS y JSON middleware, escucha en el puerto configurado (3000), monta las rutas API REST y arranca un servidor de WebSockets en el endpoint `/ws`.

**Operaciones Principales Realizadas:**
- **Autenticación (`POST /api/auth/login`):** Valida credenciales contra la base de datos, comprueba que el estado de la cuenta sea `estatus = 1` (Activo), denegando cuentas Inactivas (2) o Suspendidas (3), y verifica la contraseña cifrada con `bcrypt.compareSync`.
- **Gestión de Usuarios (`GET, POST, PUT, DELETE /api/usuarios`):** Permite listar, registrar (cifrando contraseña con Bcrypt), actualizar datos y ejecutar eliminación lógica (cambiando `estatus = 3`).
- **Gestión de Pedidos (`GET, POST, PUT, PATCH /api/pedidos`):**
  - Creación de pedidos (`POST /api/pedidos`) en una transacción SQL con registro automático en `historialestatus`.
  - Transiciones de estado de pedidos (`PATCH /api/pedidos/:id/estatus`) con transacciones SQL (`BEGIN`, `COMMIT`, `ROLLBACK`) para garantizar que la actualización del pedido y la inserción en `historialestatus` sean atómicas.
  - Notificación en tiempo real a todos los clientes WebSocket mediante la función `broadcast()`.
- **Telemetría GPS (`POST /api/ubicaciones`):** Registra coordenadas y nivel de batería del repartidor y transmite la ubicación actualizada por WebSockets.
- **Servidor WebSocket (`/ws`):** Al conectar un cliente (ej. Dashboard de Android TV o app móvil), le transfiere inmediatamente un "snapshot" de datos iniciales (`getSnapshotData()`), que incluye pedidos activos, última posición GPS conocida de la flotilla y KPIs logísticos globales.

```typescript
import express, { Request, Response } from 'express';
import cors from 'cors';
import bcrypt from 'bcryptjs';
import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { query } from './db';

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Mapeo numérico de códigos de estatus a descripciones de texto para auditoría
const statusMapping: { [key: number]: string } = {
  1: 'Aceptado',
  2: 'Pendiente',
  3: 'En ruta',
  4: 'Cancelado',
  5: 'Retrasado',
  6: 'Entregado'
};

// Conjunto de clientes WebSockets activos
const clients = new Set<WebSocket>();

/**
 * Transmite un mensaje en formato JSON a todos los clientes WebSockets conectados.
 */
const broadcast = (message: any) => {
  const payload = JSON.stringify(message);
  for (const client of clients) {
    if (client.readyState === WebSocket.OPEN) {
      try {
        client.send(payload);
      } catch (error) {
        console.error('Error enviando broadcast a cliente WebSocket:', error);
      }
    }
  }
};

/**
 * Obtiene la captura completa de datos iniciales (Snapshot) para clientes de telemetría (Dashboard TV).
 */
const getSnapshotData = async () => {
  // 1. Pedidos activos (estatus 1: Aceptado, 2: Pendiente, 3: En ruta, 5: Retrasado)
  const ordersResult = await query(
    `SELECT id_pedido, nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, estatus, fecha, hora, id_repartidor
     FROM pedido
     WHERE estatus IN (1, 2, 3, 5)
     ORDER BY id_pedido DESC`
  );

  // 2. Última ubicación GPS registrada de cada repartidor
  const gpsResult = await query(
    `SELECT DISTINCT ON (id_repartidor) id_repartidor, latitud as lat, longitud as lng, velocidad, bateria, fecha_hora
     FROM ubicaciongps
     ORDER BY id_repartidor, fecha_hora DESC`
  );

  // 3. Cálculo de Indicadores Clave de Rendimiento (KPIs)
  const kpisResult = await query(`
    SELECT
      (SELECT COUNT(*) FROM pedido WHERE estatus IN (1, 2, 3, 5))::int as "pedidosActivos",
      (SELECT COUNT(*) FROM pedido WHERE estatus = 6 AND fecha = CURRENT_DATE)::int as "entregadosHoy",
      COALESCE((
        SELECT AVG(EXTRACT(EPOCH FROM (h_entregado.fecha - h_aceptado.fecha)) / 60)
        FROM pedido p
        JOIN historialestatus h_aceptado ON p.id_pedido = h_aceptado.id_pedido AND h_aceptado.estatus = 'Aceptado'
        JOIN historialestatus h_entregado ON p.id_pedido = h_entregado.id_pedido AND h_entregado.estatus = 'Entregado'
        WHERE p.fecha = CURRENT_DATE
      ), 0)::int as "tiempoPromedioMin",
      (SELECT COUNT(*) FROM pedido WHERE estatus = 5)::int as "incidencias",
      (SELECT COUNT(DISTINCT id_repartidor) FROM pedido WHERE estatus = 3)::int as "repartidoresEnRuta"
  `);

  return {
    pedidos: ordersResult.rows.map(o => ({
      ...o,
      id_pedido: Number(o.id_pedido),
      estatus: Number(o.estatus),
      id_repartidor: o.id_repartidor ? Number(o.id_repartidor) : null
    })),
    repartidores: gpsResult.rows.map(g => ({
      repartidorId: Number(g.id_repartidor),
      lat: Number(g.lat),
      lng: Number(g.lng),
      velocidad: Number(g.velocidad),
      bateria: Number(g.bateria)
    })),
    kpis: kpisResult.rows[0] || {
      pedidosActivos: 0,
      entregadosHoy: 0,
      tiempoPromedioMin: 0,
      incidencias: 0,
      repartidoresEnRuta: 0
    }
  };
};

// Endpoint de verificación de estado de salud (Health Check)
app.get('/health', (req: Request, res: Response) => {
  res.json({ status: 'ok', timestamp: new Date() });
});

/**
 * POST /api/auth/login
 * Autentica usuarios y valida la regla estricta de estado:
 * estatus = 1 (Activo) -> Permitido
 * estatus = 2 (Inactivo) -> Denegado (403)
 * estatus = 3 (Suspensión) -> Denegado (403)
 */
app.post('/api/auth/login', async (req: Request, res: Response) => {
  const { telefono, contrasena } = req.body;

  if (!telefono || !contrasena) {
    return res.status(400).json({ error: 'Teléfono y contraseña son requeridos' });
  }

  try {
    const result = await query(
      'SELECT id_user, nombre_completo, telefono, contrasena, rol, estatus FROM usuario WHERE telefono = $1',
      [telefono.toString().trim()]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Credenciales inválidas. Usuario no encontrado.' });
    }

    const user = result.rows[0];

    // Regla de validación de estatus
    if (user.estatus === 2) {
      return res.status(403).json({ error: 'Acceso denegado: Tu cuenta se encuentra INACTIVA. Contacta al administrador.' });
    }

    if (user.estatus === 3) {
      return res.status(403).json({ error: 'Acceso denegado: Tu cuenta se encuentra SUSPENDIDA. Contacta al administrador.' });
    }

    if (user.estatus !== 1) {
      return res.status(403).json({ error: 'Acceso denegado: Estado de cuenta no autorizado.' });
    }

    // Verificación de contraseña mediante Bcrypt (o texto plano para semillas iniciales)
    let isPasswordValid = false;
    if (user.contrasena.startsWith('$2a$') || user.contrasena.startsWith('$2b$')) {
      isPasswordValid = bcrypt.compareSync(contrasena, user.contrasena);
    } else {
      isPasswordValid = (user.contrasena === contrasena);
    }

    if (!isPasswordValid) {
      return res.status(401).json({ error: 'Credenciales inválidas. Contraseña incorrecta.' });
    }

    res.json({
      message: 'Inicio de sesión exitoso',
      user: {
        id_user: user.id_user,
        nombre_completo: user.nombre_completo,
        telefono: user.telefono,
        rol: user.rol,
        estatus: user.estatus
      }
    });
  } catch (error: any) {
    console.error('Error en login:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * GET /api/pedidos/repartidor/:id
 * Retorna todos los pedidos asignados a un repartidor específico.
 */
app.get('/api/pedidos/repartidor/:id', async (req: Request, res: Response) => {
  const courierId = parseInt(req.params.id, 10);
  if (isNaN(courierId)) {
    return res.status(400).json({ error: 'ID de repartidor inválido' });
  }

  try {
    const result = await query(
      `SELECT id_pedido, nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, estatus, fecha, hora
       FROM pedido
       WHERE id_repartidor = $1
       ORDER BY id_pedido DESC`,
      [courierId]
    );
    res.json(result.rows);
  } catch (error: any) {
    console.error('Error al obtener pedidos del repartidor:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * GET /api/pedidos/admin/activos
 * Retorna todos los pedidos registrados para el Dashboard del Administrador.
 */
app.get('/api/pedidos/admin/activos', async (req: Request, res: Response) => {
  try {
    const result = await query(
      `SELECT p.id_pedido, p.nombre_cliente, p.telefono, p.direccion, p.referencia_lugar, p.descripcion_pedido, p.estatus, p.fecha, p.hora,
              u.nombre_completo AS repartidor_nombre, u.id_user AS id_repartidor
       FROM pedido p
       LEFT JOIN usuario u ON p.id_repartidor = u.id_user
       ORDER BY p.id_pedido DESC`,
      []
    );
    res.json(result.rows);
  } catch (error: any) {
    console.error('Error al obtener pedidos para administración:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * GET /api/pedidos/activo
 * Retorna el pedido activo (estatus IN (1,2,3,5)) asignado a un repartidor (usado por Wear OS).
 */
app.get('/api/pedidos/activo', async (req: Request, res: Response) => {
  const { repartidorId } = req.query;

  if (!repartidorId) {
    return res.status(400).json({ error: 'El parámetro repartidorId es requerido' });
  }

  try {
    const parsedRepartidorId = parseInt(repartidorId as string, 10);
    if (isNaN(parsedRepartidorId)) {
      return res.status(400).json({ error: 'repartidorId debe ser un número válido' });
    }

    const result = await query(
      `SELECT id_pedido, nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, estatus 
       FROM pedido 
       WHERE id_repartidor = $1 AND estatus IN (1, 2, 3, 5) 
       ORDER BY id_pedido DESC 
       LIMIT 1`,
      [parsedRepartidorId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ message: 'No hay pedidos activos asignados a este repartidor' });
    }

    res.json(result.rows[0]);
  } catch (error: any) {
    console.error('Error al obtener pedido activo:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * PATCH /api/pedidos/:id/estatus
 * Actualiza el estatus de un pedido y registra el cambio en la tabla de auditoría historialestatus mediante una transacción SQL.
 */
app.patch('/api/pedidos/:id/estatus', async (req: Request, res: Response) => {
  const orderId = parseInt(req.params.id, 10);
  const { estatus, repartidorId } = req.body;

  if (isNaN(orderId)) {
    return res.status(400).json({ error: 'ID de pedido inválido' });
  }

  if (typeof estatus !== 'number') {
    return res.status(400).json({ error: 'El campo estatus (número) es requerido' });
  }

  const statusStr = statusMapping[estatus];
  if (!statusStr) {
    return res.status(400).json({ error: `Código de estatus inválido: ${estatus}. Debe estar entre 1 y 6.` });
  }

  try {
    const orderCheck = await query('SELECT id_pedido, id_repartidor FROM pedido WHERE id_pedido = $1', [orderId]);

    if (orderCheck.rows.length === 0) {
      return res.status(404).json({ error: 'Pedido no encontrado' });
    }

    if (repartidorId && orderCheck.rows[0].id_repartidor !== repartidorId) {
      return res.status(403).json({ error: 'Este pedido no está asignado al repartidor especificado' });
    }

    const activeUserId = repartidorId || orderCheck.rows[0].id_repartidor || 1;

    // Inicio de transacción SQL atómica
    await query('BEGIN');

    const updateResult = await query(
      'UPDATE pedido SET estatus = $1 WHERE id_pedido = $2 RETURNING *',
      [estatus, orderId]
    );

    await query(
      `INSERT INTO historialestatus (estatus, id_usuario, id_pedido) 
       VALUES ($1, $2, $3)`,
      [statusStr, activeUserId, orderId]
    );

    await query('COMMIT');

    // Emitir actualización vía WebSockets a clientes conectados
    broadcast({
      type: 'pedido_actualizado',
      pedidoId: orderId,
      nuevoEstatus: estatus
    });

    res.json({
      message: 'Estatus de pedido actualizado exitosamente',
      pedido: updateResult.rows[0]
    });
  } catch (error: any) {
    await query('ROLLBACK');
    console.error('Error al actualizar estatus de pedido:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * GET /api/usuarios
 * Retorna todos los usuarios registrados (excluyendo eliminados lógicamente estatus = 3).
 */
app.get('/api/usuarios', async (req: Request, res: Response) => {
  try {
    const result = await query(
      `SELECT id_user, nombre_completo, telefono, rol, estatus 
       FROM usuario 
       WHERE estatus != 3
       ORDER BY id_user ASC`,
      []
    );
    res.json(result.rows);
  } catch (error: any) {
    console.error('Error al obtener usuarios:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * GET /api/usuarios/repartidores
 * Retorna todos los repartidores activos (rol = 2, estatus = 1).
 */
app.get('/api/usuarios/repartidores', async (req: Request, res: Response) => {
  try {
    const result = await query(
      `SELECT id_user, nombre_completo, telefono 
       FROM usuario 
       WHERE rol = 2 AND estatus = 1 
       ORDER BY nombre_completo ASC`,
      []
    );
    res.json(result.rows);
  } catch (error: any) {
    console.error('Error al obtener repartidores:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * POST /api/usuarios
 * Registra un nuevo usuario cifrando la contraseña con Bcrypt.
 */
app.post('/api/usuarios', async (req: Request, res: Response) => {
  const { nombre_completo, telefono, contrasena, rol, estatus } = req.body;

  if (!nombre_completo || !telefono || !contrasena || !rol) {
    return res.status(400).json({ error: 'nombre_completo, telefono, contrasena y rol son requeridos' });
  }

  try {
    const checkPhone = await query('SELECT id_user FROM usuario WHERE telefono = $1', [telefono.toString().trim()]);
    if (checkPhone.rows.length > 0) {
      return res.status(400).json({ error: 'Ya existe un usuario registrado con este número de teléfono' });
    }

    // Cifrar contraseña con Bcrypt
    const hashedPassword = bcrypt.hashSync(contrasena, 10);
    const userRole = parseInt(rol, 10);
    const userEstatus = estatus ? parseInt(estatus, 10) : 1;

    const insertResult = await query(
      `INSERT INTO usuario (nombre_completo, telefono, contrasena, rol, estatus)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id_user, nombre_completo, telefono, rol, estatus`,
      [nombre_completo, telefono.toString().trim(), hashedPassword, userRole, userEstatus]
    );

    res.status(201).json({
      message: 'Usuario registrado exitosamente',
      usuario: insertResult.rows[0]
    });
  } catch (error: any) {
    console.error('Error al crear usuario:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * PUT /api/usuarios/:id
 * Actualiza los datos o la contraseña de un usuario existente.
 */
app.put('/api/usuarios/:id', async (req: Request, res: Response) => {
  const userId = parseInt(req.params.id, 10);
  const { nombre_completo, telefono, contrasena, rol, estatus } = req.body;

  if (isNaN(userId)) {
    return res.status(400).json({ error: 'ID de usuario inválido' });
  }

  try {
    let updateFields: string[] = [];
    let params: any[] = [];
    let paramIndex = 1;

    if (nombre_completo) {
      updateFields.push(`nombre_completo = $${paramIndex++}`);
      params.push(nombre_completo);
    }
    if (telefono) {
      updateFields.push(`telefono = $${paramIndex++}`);
      params.push(telefono.toString().trim());
    }
    if (contrasena && contrasena.trim().length > 0) {
      const hashed = bcrypt.hashSync(contrasena, 10);
      updateFields.push(`contrasena = $${paramIndex++}`);
      params.push(hashed);
    }
    if (rol) {
      updateFields.push(`rol = $${paramIndex++}`);
      params.push(parseInt(rol, 10));
    }
    if (estatus) {
      updateFields.push(`estatus = $${paramIndex++}`);
      params.push(parseInt(estatus, 10));
    }

    if (updateFields.length === 0) {
      return res.status(400).json({ error: 'No hay campos para actualizar' });
    }

    params.push(userId);
    const queryStr = `UPDATE usuario SET ${updateFields.join(', ')} WHERE id_user = $${paramIndex} RETURNING id_user, nombre_completo, telefono, rol, estatus`;

    const result = await query(queryStr, params);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Usuario no encontrado' });
    }

    res.json({ message: 'Usuario actualizado exitosamente', usuario: result.rows[0] });
  } catch (error: any) {
    console.error('Error al actualizar usuario:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * DELETE /api/usuarios/:id
 * Eliminación lógica de un usuario (cambia el estatus a 3: Suspensión).
 */
app.delete('/api/usuarios/:id', async (req: Request, res: Response) => {
  const userId = parseInt(req.params.id, 10);
  if (isNaN(userId)) {
    return res.status(400).json({ error: 'ID de usuario inválido' });
  }

  try {
    const result = await query('UPDATE usuario SET estatus = 3 WHERE id_user = $1 RETURNING id_user, estatus', [userId]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Usuario no encontrado' });
    }
    res.json({ message: 'Usuario suspendido / eliminado lógicamente exitosamente', usuario: result.rows[0] });
  } catch (error: any) {
    console.error('Error al eliminar usuario:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * GET /api/pedidos/:id
 * Retorna los detalles completos de un pedido específico.
 */
app.get('/api/pedidos/:id', async (req: Request, res: Response) => {
  const orderId = parseInt(req.params.id, 10);
  if (isNaN(orderId)) {
    return res.status(400).json({ error: 'ID de pedido inválido' });
  }

  try {
    const result = await query(
      `SELECT p.id_pedido, p.nombre_cliente, p.telefono, p.direccion, p.referencia_lugar, p.descripcion_pedido, p.estatus, p.fecha, p.hora, p.id_repartidor,
              u.nombre_completo AS repartidor_nombre, u.telefono AS repartidor_telefono
       FROM pedido p
       LEFT JOIN usuario u ON p.id_repartidor = u.id_user
       WHERE p.id_pedido = $1`,
      [orderId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Pedido no encontrado' });
    }

    res.json(result.rows[0]);
  } catch (error: any) {
    console.error('Error al obtener detalle del pedido:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * PUT /api/pedidos/:id
 * Edita los datos de un pedido existente.
 */
app.put('/api/pedidos/:id', async (req: Request, res: Response) => {
  const orderId = parseInt(req.params.id, 10);
  const { nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, id_repartidor, estatus } = req.body;

  if (isNaN(orderId)) {
    return res.status(400).json({ error: 'ID de pedido inválido' });
  }

  try {
    let updateFields: string[] = [];
    let params: any[] = [];
    let paramIndex = 1;

    if (nombre_cliente) {
      updateFields.push(`nombre_cliente = $${paramIndex++}`);
      params.push(nombre_cliente);
    }
    if (telefono) {
      updateFields.push(`telefono = $${paramIndex++}`);
      params.push(telefono.toString().trim());
    }
    if (direccion) {
      updateFields.push(`direccion = $${paramIndex++}`);
      params.push(direccion);
    }
    if (referencia_lugar !== undefined) {
      updateFields.push(`referencia_lugar = $${paramIndex++}`);
      params.push(referencia_lugar);
    }
    if (descripcion_pedido !== undefined) {
      updateFields.push(`descripcion_pedido = $${paramIndex++}`);
      params.push(descripcion_pedido);
    }
    if (id_repartidor) {
      updateFields.push(`id_repartidor = $${paramIndex++}`);
      params.push(parseInt(id_repartidor, 10));
    }
    if (estatus) {
      updateFields.push(`estatus = $${paramIndex++}`);
      params.push(parseInt(estatus, 10));
    }

    if (updateFields.length === 0) {
      return res.status(400).json({ error: 'No hay campos para actualizar' });
    }

    params.push(orderId);
    const queryStr = `UPDATE pedido SET ${updateFields.join(', ')} WHERE id_pedido = $${paramIndex} RETURNING *`;

    const result = await query(queryStr, params);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Pedido no encontrado' });
    }

    res.json({ message: 'Pedido actualizado exitosamente', pedido: result.rows[0] });
  } catch (error: any) {
    console.error('Error al actualizar pedido:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * POST /api/pedidos
 * Registra un nuevo pedido en el sistema en una transacción SQL y emite el evento vía WebSockets.
 */
app.post('/api/pedidos', async (req: Request, res: Response) => {
  const { nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, id_repartidor } = req.body;

  if (!nombre_cliente || !telefono || !direccion || !id_repartidor) {
    return res.status(400).json({ error: 'nombre_cliente, telefono, direccion e id_repartidor son requeridos' });
  }

  try {
    await query('BEGIN');

    const insertResult = await query(
      `INSERT INTO pedido (nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, estatus, id_repartidor, fecha, hora)
       VALUES ($1, $2, $3, $4, $5, 2, $6, CURRENT_DATE, CURRENT_TIMESTAMP)
       RETURNING *`,
      [nombre_cliente, telefono, direccion, referencia_lugar || '', descripcion_pedido || '', id_repartidor]
    );

    const newOrder = insertResult.rows[0];

    await query(
      `INSERT INTO historialestatus (estatus, id_usuario, id_pedido)
       VALUES ('Pendiente', $1, $2)`,
      [id_repartidor, newOrder.id_pedido]
    );

    await query('COMMIT');

    const formattedOrder = {
      ...newOrder,
      id_pedido: Number(newOrder.id_pedido),
      estatus: Number(newOrder.estatus),
      id_repartidor: Number(newOrder.id_repartidor)
    };

    // Notificar creación vía WebSockets
    broadcast({
      type: 'pedido_creado',
      pedido: formattedOrder
    });

    res.status(201).json({
      message: 'Pedido creado exitosamente',
      pedido: formattedOrder
    });
  } catch (error: any) {
    await query('ROLLBACK');
    console.error('Error al crear pedido:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

/**
 * POST /api/ubicaciones
 * Registra las coordenadas GPS y estado de batería transmitidos por un repartidor.
 */
app.post('/api/ubicaciones', async (req: Request, res: Response) => {
  const { lat, lng, velocidad, bateria, repartidorId } = req.body;

  if (lat === undefined || lng === undefined || !repartidorId) {
    return res.status(400).json({ error: 'lat, lng y repartidorId son requeridos' });
  }

  try {
    const parsedRepartidorId = parseInt(repartidorId, 10);
    const parsedLat = parseFloat(lat);
    const parsedLng = parseFloat(lng);
    const parsedVelocidad = velocidad !== undefined ? parseFloat(velocidad) : 0;
    const parsedBateria = bateria !== undefined ? parseInt(bateria, 10) : 100;

    const result = await query(
      `INSERT INTO ubicaciongps (latitud, longitud, velocidad, bateria, id_repartidor, fecha_hora)
       VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP)
       RETURNING *`,
      [parsedLat, parsedLng, parsedVelocidad, parsedBateria, parsedRepartidorId]
    );

    // Emitir actualización de posición GPS por WebSockets
    broadcast({
      type: 'ubicacion_actualizada',
      repartidorId: parsedRepartidorId,
      lat: parsedLat,
      lng: parsedLng,
      velocidad: parsedVelocidad,
      bateria: parsedBateria
    });

    res.status(201).json({
      message: 'Ubicación registrada exitosamente',
      ubicacion: result.rows[0]
    });
  } catch (error: any) {
    console.error('Error al insertar ubicación:', error);
    res.status(500).json({ error: 'Error interno del servidor', details: error.message });
  }
});

// Inicialización del Servidor HTTP y Servidor WebSocket (/ws)
const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (request, socket, head) => {
  const pathname = new URL(request.url || '', `http://${request.headers.host}`).pathname;
  if (pathname === '/ws') {
    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit('connection', ws, request);
    });
  } else {
    socket.destroy();
  }
});

wss.on('connection', async (ws) => {
  clients.add(ws);
  console.log(`[ws]: Cliente conectado. Clientes totales: ${clients.size}`);

  // Enviar captura inicial de datos al conectar
  try {
    const snapshot = await getSnapshotData();
    ws.send(JSON.stringify({ type: 'snapshot', data: snapshot }));
  } catch (error) {
    console.error('Error al obtener snapshot inicial para el nuevo cliente WebSocket:', error);
  }

  ws.on('close', () => {
    clients.delete(ws);
    console.log(`[ws]: Cliente desconectado. Clientes totales: ${clients.size}`);
  });

  ws.on('error', (err) => {
    console.error('[ws]: Error en socket de cliente:', err);
    clients.delete(ws);
  });
});

server.listen(port, () => {
  console.log(`[server]: Servidor corriendo exitosamente en http://localhost:${port}`);
});
```
