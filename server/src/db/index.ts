import { Pool, PoolConfig } from 'pg';
import { drizzle, NodePgDatabase } from 'drizzle-orm/node-postgres';
import * as schema from './schema';
import {
  monitorPool,
  dbLogger,
  DbLogger,
  scrubSensitiveFields,
  registerPoolListener,
  initDbPoolLogger,
  DbPoolStatus,
  StructuredDbLogEntry,
} from '../utils/dbLogger';
import {
  sanitizeLogMessage,
  sanitizeConnectionUri,
  maskSensitiveFields,
  sanitizeLog,
} from '../utils/logSanitizer';
import { PgConnectionListener, PoolMetricsSnapshot } from './pg-pool-listener';

export * from './db.service';
export * from './db.module';
export * from './schema';
export * from './pg-pool-listener';
export {
  monitorPool,
  dbLogger,
  DbLogger,
  scrubSensitiveFields,
  registerPoolListener,
  initDbPoolLogger,
  DbPoolStatus,
  StructuredDbLogEntry,
  sanitizeLogMessage,
  sanitizeConnectionUri,
  maskSensitiveFields,
  sanitizeLog,
};

// Database connection configuration
const databaseUrl =
  process.env.DATABASE_URL ||
  'postgresql://gateway_admin:gateway_secure_pass_2026@localhost:5432/rewards_gateway';

const isSslRequired =
  databaseUrl.includes('sslmode=require') ||
  databaseUrl.includes('railway') ||
  databaseUrl.includes('supabase') ||
  process.env.NODE_ENV === 'production';

/**
 * Initialized application PostgreSQL connection pool.
 */
export const pool: Pool = new Pool({
  connectionString: databaseUrl,
  ssl: isSslRequired ? { rejectUnauthorized: false } : false,
  max: parseInt(process.env.DATABASE_POOL_MAX || '20', 10),
  min: parseInt(process.env.DATABASE_POOL_MIN || '2', 10),
  idleTimeoutMillis: parseInt(process.env.DATABASE_IDLE_TIMEOUT_MS || '30000', 10),
  connectionTimeoutMillis: parseInt(process.env.DATABASE_CONN_TIMEOUT_MS || '5000', 10),
});

// Invoke monitorPool with the initialized pg-pool instance to enable production-ready metrics and observability
monitorPool(pool, 'main_database_pool');

/**
 * Initialized Drizzle ORM database instance backed by the instrumented connection pool.
 */
export const db: NodePgDatabase<typeof schema> = drizzle(pool, { schema });

// Singleton pool and db instances references
let defaultPoolInstance: Pool = pool;
let defaultDbInstance: NodePgDatabase<typeof schema> = db;

/**
 * Creates and initializes a new pg.Pool instance, attaching the monitorPool utility
 * for production metrics, event tracing ('connect', 'acquire', 'remove', 'release', 'error'),
 * latency alerts, queue depth, and safe credential redaction.
 */
export function createInstrumentedPool(
  config: PoolConfig = {},
  poolName: string = 'main_database_pool'
): Pool {
  const customPool = new Pool(config);

  // Attach monitorPool listeners ('connect', 'acquire', 'remove', 'release', 'error')
  monitorPool(customPool, poolName);

  return customPool;
}

/**
 * Attaches monitorPool listeners to an existing pg.Pool instance to enable production-ready metrics.
 */
export function attachPoolLogger(poolInstance: Pool, poolName: string = 'main_database_pool'): void {
  monitorPool(poolInstance, poolName);
}

/**
 * Helper to initialize a Drizzle ORM database instance with an instrumented pg.Pool.
 */
export function createInstrumentedDb(
  config: PoolConfig = {},
  poolName: string = 'main_database_pool'
): { pool: Pool; db: NodePgDatabase<typeof schema> } {
  const customPool = createInstrumentedPool(config, poolName);
  const customDb = drizzle(customPool, { schema });
  return { pool: customPool, db: customDb };
}

/**
 * Initializes or retrieves the default database connection pool singleton with monitorPool attached.
 */
export function initDbPool(
  config?: PoolConfig,
  poolName: string = 'main_database_pool'
): { pool: Pool; db: NodePgDatabase<typeof schema> } {
  if (config && Object.keys(config).length > 0) {
    const customPool = createInstrumentedPool(config, poolName);
    const customDb = drizzle(customPool, { schema });
    defaultPoolInstance = customPool;
    defaultDbInstance = customDb;
    return { pool: customPool, db: customDb };
  }

  return { pool: defaultPoolInstance, db: defaultDbInstance };
}

/**
 * Returns the current default database pool instance.
 */
export function getDefaultPool(): Pool {
  return defaultPoolInstance;
}

/**
 * Returns the current default database Drizzle instance.
 */
export function getDefaultDb(): NodePgDatabase<typeof schema> {
  return defaultDbInstance;
}

/**
 * Retrieves the current pool status metrics from an instrumented pool.
 */
export function getPoolMetrics(poolInstance?: Pool): DbPoolStatus {
  const targetPool = poolInstance || defaultPoolInstance;
  if (!targetPool) {
    return {
      totalCount: 0,
      idleCount: 0,
      waitingCount: 0,
      queueDepth: 0,
      maxCapacity: 0,
      activeCount: 0,
      utilizationPercent: 0,
    };
  }
  return DbLogger.getPoolStatus(targetPool);
}

export default {
  pool,
  db,
  createInstrumentedPool,
  attachPoolLogger,
  createInstrumentedDb,
  initDbPool,
  getDefaultPool,
  getDefaultDb,
  getPoolMetrics,
  monitorPool,
  registerPoolListener,
  initDbPoolLogger,
  scrubSensitiveFields,
  dbLogger,
  DbLogger,
};
