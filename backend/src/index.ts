import express, { Request, Response } from 'express';
import cors from 'cors';
import bcrypt from 'bcryptjs';
import { query } from './db';

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Mapping status codes to strings for the HistorialEstatus audit table
const statusMapping: { [key: number]: string } = {
  1: 'Aceptado',
  2: 'Pendiente',
  3: 'En ruta',
  4: 'Cancelado',
  5: 'Retrasado',
  6: 'Entregado'
};

// Health Check
app.get('/health', (req: Request, res: Response) => {
  res.json({ status: 'ok', timestamp: new Date() });
});

/**
  * POST /api/auth/login
  * Body: { telefono, contrasena }
  * Authenticates user and verifies status rule:
  * estatus = 1 (Activo) -> Allowed
  * estatus = 2 (Inactivo) -> Denied
  * estatus = 3 (Suspensión) -> Denied
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

    // Status Verification Rule: Only estatus = 1 is allowed to login
    if (user.estatus === 2) {
      return res.status(403).json({ error: 'Acceso denegado: Tu cuenta se encuentra INACTIVA. Contacta al administrador.' });
    }

    if (user.estatus === 3) {
      return res.status(403).json({ error: 'Acceso denegado: Tu cuenta se encuentra SUSPENDIDA. Contacta al administrador.' });
    }

    if (user.estatus !== 1) {
      return res.status(403).json({ error: 'Acceso denegado: Estado de cuenta no autorizado.' });
    }

    // Password verification (Bcrypt or fallback plain-text for legacy seed data)
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
    console.error('Error in login:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * GET /api/pedidos/repartidor/:id
 * Returns all assigned orders for a specific courier
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
    console.error('Error fetching courier orders:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * GET /api/pedidos/admin/activos
 * Returns active orders for Admin Dashboard
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
    console.error('Error fetching admin orders:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * GET /api/pedidos/activo
 */
app.get('/api/pedidos/activo', async (req: Request, res: Response) => {
  const { repartidorId } = req.query;

  if (!repartidorId) {
    return res.status(400).json({ error: 'repartidorId query parameter is required' });
  }

  try {
    const parsedRepartidorId = parseInt(repartidorId as string, 10);
    if (isNaN(parsedRepartidorId)) {
      return res.status(400).json({ error: 'repartidorId must be a valid number' });
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
      return res.status(404).json({ message: 'No active order found for this courier' });
    }

    res.json(result.rows[0]);
  } catch (error: any) {
    console.error('Error fetching active order:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * PATCH /api/pedidos/:id/estatus
 */
app.patch('/api/pedidos/:id/estatus', async (req: Request, res: Response) => {
  const orderId = parseInt(req.params.id, 10);
  const { estatus, repartidorId } = req.body;

  if (isNaN(orderId)) {
    return res.status(400).json({ error: 'Invalid order ID' });
  }

  if (typeof estatus !== 'number' || !repartidorId) {
    return res.status(400).json({ error: 'estatus (number) and repartidorId (number) are required in request body' });
  }

  const statusStr = statusMapping[estatus];
  if (!statusStr) {
    return res.status(400).json({ error: `Invalid status code: ${estatus}. Must be between 1 and 6.` });
  }

  try {
    const orderCheck = await query(
      'SELECT id_pedido, id_repartidor FROM pedido WHERE id_pedido = $1',
      [orderId]
    );

    if (orderCheck.rows.length === 0) {
      return res.status(404).json({ error: 'Order not found' });
    }

    if (orderCheck.rows[0].id_repartidor !== repartidorId) {
      return res.status(403).json({ error: 'This order is not assigned to the specified courier' });
    }

    await query('BEGIN');

    const updateResult = await query(
      'UPDATE pedido SET estatus = $1 WHERE id_pedido = $2 RETURNING *',
      [estatus, orderId]
    );

    await query(
      `INSERT INTO historialestatus (estatus, id_usuario, id_pedido) 
       VALUES ($1, $2, $3)`,
      [statusStr, repartidorId, orderId]
    );

    await query('COMMIT');

    res.json({
      message: 'Order status updated successfully',
      pedido: updateResult.rows[0]
    });
  } catch (error: any) {
    await query('ROLLBACK');
    console.error('Error updating order status:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * GET /api/usuarios
 * Returns all registered users (Gestión de Usuarios)
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
    console.error('Error fetching users:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * GET /api/usuarios/repartidores
 * Returns all active couriers (rol = 2, estatus = 1)
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
    console.error('Error fetching repartidores:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * POST /api/usuarios
 * Body: { nombre_completo, telefono, contrasena, rol, estatus }
 * Hashes plain text password with Bcrypt before saving to database!
 */
app.post('/api/usuarios', async (req: Request, res: Response) => {
  const { nombre_completo, telefono, contrasena, rol, estatus } = req.body;

  if (!nombre_completo || !telefono || !contrasena || !rol) {
    return res.status(400).json({ error: 'nombre_completo, telefono, contrasena y rol son requeridos' });
  }

  try {
    // Check if phone already exists
    const checkPhone = await query('SELECT id_user FROM usuario WHERE telefono = $1', [telefono.toString().trim()]);
    if (checkPhone.rows.length > 0) {
      return res.status(400).json({ error: 'Ya existe un usuario registrado con este número de teléfono' });
    }

    // Hash password with Bcrypt
    const hashedPassword = bcrypt.hashSync(contrasena, 10);
    const userRole = parseInt(rol, 10);
    const userEstatus = estatus ? parseInt(estatus, 10) : 1; // Default to 1 (Activo)

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
    console.error('Error creating user:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * PUT /api/usuarios/:id
 * Updates user details, role, or status
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
    console.error('Error updating user:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * DELETE /api/usuarios/:id
 * Logical deletion: updates estatus = 3 (Suspensión)
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
    console.error('Error deleting user:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * GET /api/pedidos/:id
 * Returns details of a specific order
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
    console.error('Error fetching order details:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * PUT /api/pedidos/:id
 * Updates an existing order's information (client name, phone, address, reference, description, courier)
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
    console.error('Error updating order:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

/**
 * POST /api/pedidos
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

    res.status(201).json({
      message: 'Pedido creado exitosamente',
      pedido: newOrder
    });
  } catch (error: any) {
    await query('ROLLBACK');
    console.error('Error creating order:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

// Start Server
app.listen(port, () => {
  console.log(`[server]: Server is running at http://localhost:${port}`);
});
