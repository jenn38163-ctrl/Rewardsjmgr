import { Injectable, OnModuleInit, OnModuleDestroy, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { drizzle, NodePgDatabase } from 'drizzle-orm/node-postgres';
import { Pool, PoolClient, PoolConfig } from 'pg';
import * as schema from './schema';
import { PgConnectionListener, PoolMetricsSnapshot, sanitizeDatabaseUri } from './pg-pool-listener';
import { DbLogger, registerPoolListener, monitorPool } from '../utils/dbLogger';

export interface PgPoolStats extends PoolMetricsSnapshot {
  uptimeSeconds: number;
  lastLatencyMs?: number;
}

@Injectable()
export class DbService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(DbService.name);
  private pool: Pool;
  public db: NodePgDatabase<typeof schema>;
  private poolListener: PgConnectionListener = new PgConnectionListener();
  private poolMetricsInterval: NodeJS.Timeout | null = null;
  private startTime: number = Date.now();
  private lastPingLatencyMs: number = 0;

  constructor(private configService: ConfigService) {}

  async onModuleInit() {
    const rawDatabaseUrl = this.configService.get<string>('DATABASE_URL') || 
      'postgresql://gateway_admin:gateway_secure_pass_2026@localhost:5432/rewards_gateway';

    const maxPoolSize = parseInt(this.configService.get<string>('DATABASE_POOL_MAX') || '20', 10);
    const minPoolSize = parseInt(this.configService.get<string>('DATABASE_POOL_MIN') || '2', 10);
    const idleTimeoutMillis = parseInt(this.configService.get<string>('DATABASE_IDLE_TIMEOUT_MS') || '30000', 10);
    const connectionTimeoutMillis = parseInt(this.configService.get<string>('DATABASE_CONN_TIMEOUT_MS') || '5000', 10);

    const isSslRequired = rawDatabaseUrl.includes('sslmode=require') || 
      rawDatabaseUrl.includes('railway') || 
      rawDatabaseUrl.includes('supabase') ||
      process.env.NODE_ENV === 'production';

    const poolConfig: PoolConfig = {
      connectionString: rawDatabaseUrl,
      ssl: isSslRequired ? { rejectUnauthorized: false } : false,
      max: maxPoolSize,
      min: minPoolSize,
      idleTimeoutMillis,
      connectionTimeoutMillis,
    };

    const sanitizedUri = sanitizeDatabaseUri(rawDatabaseUrl);
    this.poolListener.emitStructured('info', {
      event: 'pg_pool_initializing',
      message: 'Initializing PostgreSQL connection pool with structured observability',
      databaseUri: sanitizedUri,
      config: {
        max: maxPoolSize,
        min: minPoolSize,
        idleTimeoutMillis,
        connectionTimeoutMillis,
        ssl: isSslRequired,
      },
    });

    try {
      this.pool = new Pool(poolConfig);

      // Register dbLogger listeners with the pg-pool instance for production monitoring of pool metrics
      monitorPool(this.pool, 'main_database_pool');

      // Attach PgConnectionListener to track metrics snapshot
      this.poolListener.attach(this.pool, {
        max: maxPoolSize,
        min: minPoolSize,
        idleTimeoutMillis,
        connectionTimeoutMillis,
        ssl: isSslRequired,
      });

      this.db = drizzle(this.pool, { schema });

      // Run immediate connection check & latency probe
      const pingResult = await this.pingDatabase();
      if (pingResult.ok) {
        this.poolListener.emitStructured('info', {
          event: 'pg_pool_ready',
          message: 'PostgreSQL connection pool verified and operational',
          latencyMs: pingResult.latencyMs,
          pool: this.getPoolStats(),
        });
      } else {
        this.poolListener.emitStructured('warn', {
          event: 'pg_pool_initial_ping_failed',
          message: 'Initial ping to PostgreSQL database failed',
          error: pingResult.error,
        });
      }

      await this.seedChartOfAccounts();

      // Start periodic structured pool metrics logging (every 60 seconds)
      this.poolMetricsInterval = setInterval(() => {
        this.logPeriodicPoolMetrics();
      }, 60000);

    } catch (error) {
      this.poolListener.emitStructured('error', {
        event: 'pg_pool_init_failed',
        message: `Failed to initialize PostgreSQL pool: ${error.message}`,
        error: error.message,
        stack: error.stack,
        severity: 'CRITICAL',
      });
    }
  }

  async onModuleDestroy() {
    if (this.poolMetricsInterval) {
      clearInterval(this.poolMetricsInterval);
      this.poolMetricsInterval = null;
    }
    if (this.pool) {
      this.poolListener.emitStructured('info', {
        event: 'pg_pool_destroying',
        message: 'Draining and closing PostgreSQL connection pool...',
        pool: this.getPoolStats(),
      });
      await this.pool.end();
      this.poolListener.emitStructured('info', {
        event: 'pg_pool_closed',
        message: 'PostgreSQL connection pool closed successfully.',
      });
    }
  }

  /**
   * Execute query or callback with connection checkout tracking, latency timing, and structured error reporting.
   */
  async withClient<T>(fn: (client: PoolClient) => Promise<T>, operationName = 'db_operation'): Promise<T> {
    if (!this.pool) {
      throw new Error('PostgreSQL pool is not initialized');
    }

    const checkoutStart = process.hrtime.bigint();
    let client: PoolClient | null = null;

    try {
      client = await this.pool.connect();
      const checkoutEnd = process.hrtime.bigint();
      const checkoutLatencyMs = Number(checkoutEnd - checkoutStart) / 1_000_000;

      // Track checkout wait time in listener & DbLogger
      this.poolListener.recordCheckoutWait(checkoutLatencyMs, operationName);
      DbLogger.logCheckoutLatency(this.pool, checkoutLatencyMs, operationName);

      const execStart = process.hrtime.bigint();
      const result = await fn(client);
      const execEnd = process.hrtime.bigint();
      const queryDurationMs = Number(execEnd - execStart) / 1_000_000;

      if (queryDurationMs > 250) {
        this.poolListener.emitStructured('warn', {
          event: 'pg_slow_query',
          message: `Slow database operation detected: ${operationName}`,
          queryDurationMs: Number(queryDurationMs.toFixed(2)),
          operation: operationName,
        });
      }

      return result;
    } catch (err) {
      const checkoutEnd = process.hrtime.bigint();
      const elapsedMs = Number(checkoutEnd - checkoutStart) / 1_000_000;

      // Record checkout or execution failure in listener & DbLogger
      this.poolListener.recordCheckoutFailure(err, elapsedMs, operationName);
      DbLogger.logCheckoutFailure(this.pool, err, elapsedMs, operationName);
      throw err;
    } finally {
      if (client) {
        client.release();
      }
    }
  }

  /**
   * High-resolution latency probe executing SELECT 1
   */
  async pingDatabase(): Promise<{ ok: boolean; latencyMs: number; poolStats: PgPoolStats; error?: string }> {
    if (!this.pool) {
      return {
        ok: false,
        latencyMs: 0,
        poolStats: this.getPoolStats(),
        error: 'PostgreSQL connection pool not initialized',
      };
    }

    const start = process.hrtime.bigint();
    try {
      const client = await this.pool.connect();
      try {
        await client.query('SELECT 1 AS health_check');
        const end = process.hrtime.bigint();
        const latencyMs = Number((Number(end - start) / 1_000_000).toFixed(2));
        this.lastPingLatencyMs = latencyMs;

        const stats = this.getPoolStats();
        this.poolListener.emitStructured('debug', {
          event: 'pg_health_ping',
          latencyMs,
          ok: true,
          pool: stats,
        });

        return {
          ok: true,
          latencyMs,
          poolStats: stats,
        };
      } finally {
        client.release();
      }
    } catch (error) {
      const end = process.hrtime.bigint();
      const latencyMs = Number((Number(end - start) / 1_000_000).toFixed(2));

      this.poolListener.emitStructured('error', {
        event: 'pg_health_ping_failure',
        message: `PostgreSQL ping failed: ${error.message}`,
        latencyMs,
        errorCode: error.code || 'PING_FAILED',
        errorMessage: error.message,
        pool: this.getPoolStats(),
      });

      return {
        ok: false,
        latencyMs,
        poolStats: this.getPoolStats(),
        error: error.message,
      };
    }
  }

  /**
   * Telemetry snapshot of pool sizing, wait times, and utilization
   */
  getPoolStats(): PgPoolStats {
    const metrics = this.poolListener.getPoolMetrics();
    return {
      ...metrics,
      uptimeSeconds: Math.floor((Date.now() - this.startTime) / 1000),
      lastLatencyMs: this.lastPingLatencyMs,
    };
  }

  private logPeriodicPoolMetrics() {
    const stats = this.getPoolStats();
    this.poolListener.emitStructured('info', {
      event: 'pg_pool_metrics_heartbeat',
      pool: stats,
      timestamp: new Date().toISOString(),
    });
  }

  private logStructured(level: 'debug' | 'info' | 'warn' | 'error', payload: Record<string, any>) {
    this.poolListener.emitStructured(level, {
      source: 'DbService',
      ...payload,
    });
  }

  private async seedChartOfAccounts() {
    try {
      this.logStructured('info', {
        event: 'pg_seed_accounts_start',
        message: 'Initializing & Seeding Standard Chart of Accounts...',
      });

      const systemAccounts = [
        { code: 'ASSET:CASH_FLOAT_INR', name: 'Main Operating Cash Float', type: 'ASSET', currency: 'INR', balanceCents: 100000000n }, // ₹1,000,000.00
        { code: 'ASSET:ZENDIT_PREPAID_INR', name: 'Zendit Gateway Prepaid Float', type: 'ASSET', currency: 'INR', balanceCents: 50000000n },   // ₹500,000.00
        { code: 'ASSET:GIFTBIT_PREPAID_INR', name: 'Giftbit Brand Float', type: 'ASSET', currency: 'INR', balanceCents: 25000000n },  // ₹250,000.00
        { code: 'LIABILITY:USER_WALLETS', name: 'Customer Wallet Balances', type: 'LIABILITY', currency: 'INR', balanceCents: 50000000n }, // ₹500,000.00
        { code: 'LIABILITY:PENDING_DISPATCH', name: 'Orders In-Flight Escrow', type: 'LIABILITY', currency: 'INR', balanceCents: 0n },
        { code: 'REVENUE:GATEWAY_FEES', name: 'Marketplace Transaction Margin', type: 'REVENUE', currency: 'INR', balanceCents: 0n },
        { code: 'EXPENSE:GATEWAY_FEES', name: 'Provider Wholesale Cost of Goods', type: 'EXPENSE', currency: 'INR', balanceCents: 0n },
        { code: 'EXPENSE:REFUNDS', name: 'Settled Customer Refunds', type: 'EXPENSE', currency: 'INR', balanceCents: 0n }
      ];

      for (const account of systemAccounts) {
        const existing = await this.db.query.accounts.findFirst({
          where: (fields, { eq }) => eq(fields.code, account.code),
        });

        if (!existing) {
          await this.db.insert(schema.accounts).values({
            code: account.code,
            name: account.name,
            type: account.type,
            currency: account.currency,
            balanceCents: account.balanceCents,
            isActive: true,
          });
          this.logStructured('info', {
            event: 'pg_seed_account_created',
            code: account.code,
          });
        }
      }
      this.logStructured('info', {
        event: 'pg_seed_accounts_complete',
        message: 'Standard Chart of Accounts seeding complete.',
      });
    } catch (e) {
      this.logStructured('error', {
        event: 'pg_seed_accounts_failed',
        message: `Error seeding chart of accounts: ${e.message}`,
        error: e.message,
      });
    }
  }
}

