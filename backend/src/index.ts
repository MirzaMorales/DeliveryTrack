import express, { Request, Response } from 'express';
import cors from 'cors';
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
 * GET /api/pedidos/activo
 * Query Params: repartidorId (number)
 * Returns the current active order for the courier.
 * Active statuses: 1 (Aceptado), 2 (Pendiente), 3 (En ruta), 5 (Retrasado).
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

    // Query for the latest active order for this courier
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
 * Body: { estatus: number, repartidorId: number }
 * Updates order status and records a history log in historialestatus.
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
    // Check if the order exists and is assigned to this courier
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

    // Begin database transaction to update order and insert history log
    await query('BEGIN');

    // 1. Update pedido status
    const updateResult = await query(
      'UPDATE pedido SET estatus = $1 WHERE id_pedido = $2 RETURNING *',
      [estatus, orderId]
    );

    // 2. Insert into historialestatus
    await query(
      `INSERT INTO historialestatus (estatus, id_usuario, id_pedido) 
       VALUES ($1, $2, $3)`,
      [statusStr, repartidorId, orderId]
    );

    await query('COMMIT');

    res.json({
      message: 'Order status updated successfully',
      pedido: updateResult.rows[0],
      auditLog: {
        estatus: statusStr,
        id_usuario: repartidorId,
        id_pedido: orderId
      }
    });
  } catch (error: any) {
    await query('ROLLBACK');
    console.error('Error updating order status:', error);
    res.status(500).json({ error: 'Internal server error', details: error.message });
  }
});

// Start Server
app.listen(port, () => {
  console.log(`[server]: Server is running at http://localhost:${port}`);
});
