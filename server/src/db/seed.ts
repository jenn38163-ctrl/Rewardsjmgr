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

async function runSeed() {
  console.log('--- Seeding System Chart of Accounts & Baseline Floats ---');
  const pool = new Pool({
    connectionString: databaseUrl,
    ssl: isSslRequired ? { rejectUnauthorized: false } : false,
  });

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    
    // Seed standard chart of accounts
    await client.query(`
      INSERT INTO accounts (code, name, type, currency) VALUES
      ('ASSET:CASH_FLOAT_INR', 'Main Operating Cash Float', 'ASSET', 'INR'),
      ('ASSET:ZENDIT_PREPAID_USD', 'Zendit Gateway Prepaid Float', 'ASSET', 'USD'),
      ('ASSET:GIFTBIT_PREPAID_USD', 'Giftbit Brand Float', 'ASSET', 'USD'),
      ('LIABILITY:USER_WALLETS', 'Customer Wallet Balances', 'LIABILITY', 'INR'),
      ('LIABILITY:PENDING_DISPATCH', 'Orders In-Flight Escrow', 'LIABILITY', 'INR'),
      ('REVENUE:GATEWAY_FEES', 'Marketplace Transaction Margin', 'REVENUE', 'INR'),
      ('EXPENSE:GATEWAY_FEES', 'Provider Wholesale Cost of Goods', 'EXPENSE', 'INR'),
      ('EXPENSE:REFUNDS', 'Settled Customer Refunds', 'EXPENSE', 'INR')
      ON CONFLICT (code) DO NOTHING;
    `);

    await client.query('COMMIT');
    console.log(' Baseline seed data verified and loaded.');
  } catch (err: any) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('Seeding failed:', err.message || err);
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

runSeed();
