import { Pool } from 'pg';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';

dotenv.config();

// Attempt to read local.properties from root workspace directory if available
const rootLocalPropsPath = path.resolve(__dirname, '../../local.properties');
let neonConnectionString = process.env.NEON_CONN_STRING || process.env.DATABASE_URL;

if (!neonConnectionString && fs.existsSync(rootLocalPropsPath)) {
  const content = fs.readFileSync(rootLocalPropsPath, 'utf-8');
  const match = content.match(/NEON_CONN_STRING=(.+)/);
  if (match && match[1]) {
    neonConnectionString = match[1].trim();
  }
}

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

const pool = new Pool(poolConfig);

export const query = (text: string, params?: any[]) => {
  return pool.query(text, params);
};

export default pool;
