import bcrypt from 'bcryptjs';
import { query } from './db';

const createAdmin = async () => {
  console.log('--- Creating Admin User in Neon.tech PostgreSQL ---');
  try {
    const adminPhone = '5550000000';
    const plainPassword = 'admin123';
    const hashedPassword = bcrypt.hashSync(plainPassword, 10);

    // Check if admin already exists
    const existing = await query('SELECT * FROM usuario WHERE telefono = $1', [adminPhone]);

    if (existing.rows.length > 0) {
      console.log('Admin user already exists. Updating password hash...');
      await query(
        `UPDATE usuario 
         SET contrasena = $1, rol = 1, estatus = 1, nombre_completo = 'Admin General' 
         WHERE telefono = $2`,
        [hashedPassword, adminPhone]
      );
    } else {
      console.log('Inserting new Admin user...');
      await query(
        `INSERT INTO usuario (nombre_completo, telefono, contrasena, rol, estatus)
         VALUES ('Admin General', $1, $2, 1, 1)`,
        [adminPhone, hashedPassword]
      );
    }

    console.log('Admin created successfully!');
    console.log(`📱 Teléfono Admin: ${adminPhone}`);
    console.log(`🔑 Contraseña: ${plainPassword}`);
    console.log(`🔒 Contraseña Hash en Neon.tech: ${hashedPassword}`);

    console.log('\n--- Current Users in Database ---');
    const allUsers = await query('SELECT id_user, nombre_completo, telefono, contrasena, rol, estatus FROM usuario ORDER BY id_user ASC');
    console.table(allUsers.rows);

    process.exit(0);
  } catch (error) {
    console.error('Error creating admin user:', error);
    process.exit(1);
  }
};

createAdmin();
