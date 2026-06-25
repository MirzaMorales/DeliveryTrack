import fs from 'fs';
import path from 'path';
import { query } from './db';

const initDb = async () => {
  console.log('Initializing database...');
  try {
    const schemaPath = path.join(__dirname, '../schema.sql');
    const sql = fs.readFileSync(schemaPath, 'utf8');

    console.log('Executing SQL schema script...');
    await query(sql);

    console.log('Database initialized successfully with schema and seed data!');
    process.exit(0);
  } catch (error) {
    console.error('Failed to initialize database:', error);
    process.exit(1);
  }
};

initDb();
