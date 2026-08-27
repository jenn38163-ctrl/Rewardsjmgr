import { readFileSync, existsSync } from 'fs';
import { join } from 'path';
import { Pool } from 'pg';
import * as dotenv from 'dotenv';

dotenv.config({ path: ['.env', '.env.example'] });

const databaseUrl =
  process.env.DATABASE_URL ||
  'postgresql://gateway_admin:gateway_secure_pass_2026@localhost:5432/rewards_gateway';

const isSslRequired =
  databaseUrl.includes('sslmode=require') ||
  databaseUrl.includes('railway') ||
  databaseUrl.includes('supabase') ||
  process.env.NODE_ENV === 'production';

async function runMigrations() {
  console.log('--- Starting PostgreSQL Database Migrations ---');
  console.log(`Connecting to: ${databaseUrl.replace(/:[^:@]+@/, ':*****@')}`);

  const pool = new Pool({
    connectionString: databaseUrl,
    ssl: isSslRequired ? { rejectUnauthorized: false } : false,
  });

  const client = await pool.connect();
  try {
    const migrationPath = join(__dirname, '../../db/migrations/001_create_double_entry_ledger.sql');
    let sql = '';

    if (existsSync(migrationPath)) {
      sql = readFileSync(migrationPath, 'utf8');
    } else {
      // Fallback relative to project root
      const fallbackPath = join(process.cwd(), 'server/db/migrations/001_create_double_entry_ledger.sql');
      if (existsSync(fallbackPath)) {
        sql = readFileSync(fallbackPath, 'utf8');
      } else {
        throw new Error(`Migration SQL file not found at ${migrationPath} or ${fallbackPath}`);
      }
    }

    console.log('Executing 001_create_double_entry_ledger.sql migration...');
    await client.query('BEGIN');
    await client.query(sql);
    await client.query('COMMIT');
    console.log(' Migration successfully applied.');

    // Verify key tables exist
    const res = await client.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public' 
      ORDER BY table_name;
    `);
    console.log('Verified database tables in public schema:', res.rows.map(r => r.table_name).join(', '));
  } catch (err: any) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('Migration failed:', err.message || err);
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

runMigrations();
