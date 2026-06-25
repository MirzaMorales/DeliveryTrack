-- Database Schema for DeliveryTrack

CREATE TABLE IF NOT EXISTS usuario (
    id_user SERIAL PRIMARY KEY,
    nombre_completo VARCHAR(255) NOT NULL,
    telefono VARCHAR(20),
    contrasena VARCHAR(255) NOT NULL,
    rol INT NOT NULL, -- 1 = Admin, 2 = Repartidor
    estatus INT NOT NULL DEFAULT 1 -- 1 = Activo, 2 = Inactivo, 3 = Suspensión
);

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

CREATE TABLE IF NOT EXISTS historialestatus (
    id_historial SERIAL PRIMARY KEY,
    estatus VARCHAR(50) NOT NULL, -- E.g. "Aceptado", "Pendiente", "En ruta", etc., or string representing status
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    id_usuario INT NOT NULL REFERENCES usuario(id_user) ON DELETE CASCADE,
    id_pedido INT NOT NULL REFERENCES pedido(id_pedido) ON DELETE CASCADE
);

-- Seed Initial Test Data with explicit IDs
INSERT INTO usuario (id_user, nombre_completo, telefono, contrasena, rol, estatus)
VALUES (1, 'Ana Administradora', '5557654321', 'admin123', 1, 1)
ON CONFLICT DO NOTHING;

INSERT INTO usuario (id_user, nombre_completo, telefono, contrasena, rol, estatus)
VALUES (2, 'Carlos Repartidor', '5551234567', 'password123', 2, 1)
ON CONFLICT DO NOTHING;

-- Seed an active order for Carlos (repartidorId = 2)
INSERT INTO pedido (id_pedido, nombre_cliente, telefono, direccion, referencia_lugar, descripcion_pedido, estatus, id_repartidor)
VALUES (
    1,
    'Juan Pérez', 
    '5559876543', 
    'Av. Juárez 123, Col. Centro', 
    'Frente a la farmacia Guadalajara', 
    '1 Pizza Familiar Pepperoni y 1 Refresco de 2L', 
    2, -- Pendiente (2)
    2  -- Asignado a Carlos (repartidorId = 2)
)
ON CONFLICT DO NOTHING;

-- Update sequences for auto-increment
SELECT setval(pg_get_serial_sequence('usuario', 'id_user'), coalesce(max(id_user), 1)) FROM usuario;
SELECT setval(pg_get_serial_sequence('pedido', 'id_pedido'), coalesce(max(id_pedido), 1)) FROM pedido;
