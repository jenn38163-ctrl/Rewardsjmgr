import { Logger } from '@nestjs/common';
import { Pool, PoolClient } from 'pg';

export interface SanitizedPoolConfig {
  max: number;
  min: number;
  idleTimeoutMillis: number;
  connectionTimeoutMillis: number;
  host?: string;
  port?: number;
  database?: string;
  ssl: boolean;
}

export interface PoolMetricsSnapshot {
  status: 'HEALTHY' | 'DEGRADED' | 'DISCONNECTED';
  totalCount: number;
  idleCount: number;
  waitingCount: number;
  maxCapacity: number;
  activeCount: number;
  utilizationPercent: number;
  avgWaitTimeMs: number;
  recentSlowAcquires: number;
  recentFailures: number;
}

/**
 * Sanitizes connection strings and database configurations to prevent
 * accidental credential leakage into structured observability logs.
 */
export function sanitizeDatabaseUri(uri?: string): string {
  if (!uri) return '[UNDEFINED]';
  return uri.replace(/(postgres(?:ql)?:\/\/[^:]+:)([^@]+)(@.+)/i, '$1*****$3');
}

export function sanitizeLogPayload(payload: Record<string, any>): Record<string, any> {
  const sensitiveKeys = ['password', 'secret', 'token', 'authorization', 'key', 'credential', 'auth', 'pass'];
  const sanitized: Record<string, any> = {};

  for (const [key, value] of Object.entries(payload)) {
    if (sensitiveKeys.some((k) => key.toLowerCase().includes(k))) {
      sanitized[key] = '*****';
    } else if (typeof value === 'string' && value.includes('postgres://')) {
      sanitized[key] = sanitizeDatabaseUri(value);
    } else if (value && typeof value === 'object' && !Array.isArray(value) && !(value instanceof Date)) {
      sanitized[key] = sanitizeLogPayload(value);
    } else {
      sanitized[key] = value;
    }
  }
  return sanitized;
}

/**
 * Production-ready PostgreSQL Connection Pool Listener.
 * Hooks into 'connect', 'acquire', 'release', 'remove', and 'error' events
 * from the 'pg' library to track pool status, wait times, and failures with structured logging.
 */
export class PgConnectionListener {
  private readonly logger = new Logger('PgPoolListener');
  private pool: Pool | null = null;
  private config: SanitizedPoolConfig | null = null;
  
  // High-resolution telemetry buffers
  private recentWaitTimes: number[] = [];
  private slowAcquireCount = 0;
  private checkoutFailureCount = 0;
  private clientIdCounter = 0;
  private clientMetaMap = new WeakMap<PoolClient, { clientId: number; connectedAt: number; acquiredAt?: number }>();

  /**
   * Attaches the listener to the pg.Pool instance
   */
  attach(pool: Pool, sanitizedConfig: SanitizedPoolConfig) {
    this.pool = pool;
    this.config = sanitizedConfig;

    this.emitStructured('info', {
      event: 'pg_pool_attached',
      message: 'PostgreSQL connection listener active',
      config: this.config,
    });

    // 1. 'connect' - Emitted when a new physical client is created and connected to PostgreSQL
    pool.on('connect', (client: PoolClient) => {
      this.clientIdCounter++;
      const clientId = this.clientIdCounter;
      this.clientMetaMap.set(client, {
        clientId,
        connectedAt: Date.now(),
      });

      this.emitStructured('debug', {
        event: 'pg_pool_connect',
        message: 'Physical PostgreSQL client connection established',
        clientId,
        pool: this.getPoolMetrics(),
      });
    });

    // 2. 'acquire' - Emitted when a client is checked out by a query/transaction
    pool.on('acquire', (client: PoolClient) => {
      const meta = this.clientMetaMap.get(client) || { clientId: 0, connectedAt: Date.now() };
      meta.acquiredAt = Date.now();
      this.clientMetaMap.set(client, meta);

      this.emitStructured('debug', {
        event: 'pg_pool_acquire',
        message: 'Client checked out from connection pool',
        clientId: meta.clientId,
        pool: this.getPoolMetrics(),
      });
    });

    // 3. 'release' - Emitted when a client is returned to the pool
    pool.on('release', (err: Error | undefined, client: PoolClient) => {
      const meta = this.clientMetaMap.get(client);
      const holdDurationMs = meta?.acquiredAt ? Date.now() - meta.acquiredAt : 0;

      if (err) {
        this.emitStructured('warn', {
          event: 'pg_pool_release_error',
          message: 'Error encountered during client release to pool',
          clientId: meta?.clientId,
          error: err.message,
          holdDurationMs,
          pool: this.getPoolMetrics(),
        });
      } else {
        this.emitStructured('debug', {
          event: 'pg_pool_release',
          message: 'Client returned to connection pool',
          clientId: meta?.clientId,
          holdDurationMs,
          pool: this.getPoolMetrics(),
        });
      }
    });

    // 4. 'remove' - Emitted when a client is disconnected and destroyed
    pool.on('remove', (client: PoolClient) => {
      const meta = this.clientMetaMap.get(client);
      const totalLifetimeMs = meta ? Date.now() - meta.connectedAt : 0;

      this.emitStructured('debug', {
        event: 'pg_pool_remove',
        message: 'Client removed and connection closed',
        clientId: meta?.clientId,
        totalLifetimeMs,
        pool: this.getPoolMetrics(),
      });
    });

    // 5. 'error' - Emitted when an idle client encounters an unexpected backend error
    pool.on('error', (err: Error & { code?: string }, client?: PoolClient) => {
      this.checkoutFailureCount++;
      const meta = client ? this.clientMetaMap.get(client) : undefined;

      this.emitStructured('error', {
        event: 'pg_pool_client_error',
        message: 'Unexpected error on idle PostgreSQL client connection',
        errorCode: err.code || 'UNKNOWN_PG_ERR',
        errorMessage: err.message,
        clientId: meta?.clientId,
        stack: err.stack,
        pool: this.getPoolMetrics(),
        severity: 'CRITICAL',
      });
    });
  }

  /**
   * Records connection checkout latency and wait time
   */
  recordCheckoutWait(waitDurationMs: number, operationName: string) {
    this.recentWaitTimes.push(waitDurationMs);
    if (this.recentWaitTimes.length > 100) {
      this.recentWaitTimes.shift();
    }

    if (waitDurationMs >= 100) {
      this.slowAcquireCount++;
      this.emitStructured('warn', {
        event: 'pg_connection_slow_checkout',
        message: `High connection wait time (${waitDurationMs.toFixed(2)}ms) for operation '${operationName}'`,
        operation: operationName,
        waitDurationMs: Number(waitDurationMs.toFixed(2)),
        pool: this.getPoolMetrics(),
      });
    }
  }

  /**
   * Records connection checkout failures (e.g. pool exhaustion, timeout, network error)
   */
  recordCheckoutFailure(error: Error & { code?: string }, waitDurationMs: number, operationName: string) {
    this.checkoutFailureCount++;

    this.emitStructured('error', {
      event: 'pg_connection_checkout_failure',
      message: `Failed to checkout connection from pool for '${operationName}': ${error.message}`,
      operation: operationName,
      waitDurationMs: Number(waitDurationMs.toFixed(2)),
      errorCode: error.code || 'CONN_TIMEOUT_OR_EXHAUSTED',
      errorMessage: error.message,
      pool: this.getPoolMetrics(),
      severity: 'CRITICAL',
    });
  }

  /**
   * Calculates real-time pool metrics snapshot
   */
  getPoolMetrics(): PoolMetricsSnapshot {
    if (!this.pool) {
      return {
        status: 'DISCONNECTED',
        totalCount: 0,
        idleCount: 0,
        waitingCount: 0,
        maxCapacity: this.config?.max || 20,
        activeCount: 0,
        utilizationPercent: 0,
        avgWaitTimeMs: 0,
        recentSlowAcquires: this.slowAcquireCount,
        recentFailures: this.checkoutFailureCount,
      };
    }

    const totalCount = this.pool.totalCount || 0;
    const idleCount = this.pool.idleCount || 0;
    const waitingCount = this.pool.waitingCount || 0;
    const maxCapacity = (this.pool as any).options?.max || this.config?.max || 20;

    const activeCount = Math.max(0, totalCount - idleCount);
    const utilizationPercent = maxCapacity > 0 ? Number(((activeCount / maxCapacity) * 100).toFixed(1)) : 0;

    const sumWait = this.recentWaitTimes.reduce((acc, v) => acc + v, 0);
    const avgWaitTimeMs = this.recentWaitTimes.length > 0 ? Number((sumWait / this.recentWaitTimes.length).toFixed(2)) : 0;

    let status: 'HEALTHY' | 'DEGRADED' | 'DISCONNECTED' = 'HEALTHY';
    if (waitingCount > 5 || utilizationPercent >= 90 || this.checkoutFailureCount > 10) {
      status = 'DEGRADED';
    }

    return {
      status,
      totalCount,
      idleCount,
      waitingCount,
      maxCapacity,
      activeCount,
      utilizationPercent,
      avgWaitTimeMs,
      recentSlowAcquires: this.slowAcquireCount,
      recentFailures: this.checkoutFailureCount,
    };
  }

  /**
   * Emits structured JSON log with sanitized attributes
   */
  emitStructured(level: 'debug' | 'info' | 'warn' | 'error', payload: Record<string, any>) {
    const sanitized = sanitizeLogPayload({
      logger: 'PgConnectionListener',
      timestamp: new Date().toISOString(),
      ...payload,
    });

    const jsonMessage = JSON.stringify(sanitized);
    switch (level) {
      case 'debug':
        this.logger.debug(jsonMessage);
        break;
      case 'info':
        this.logger.log(jsonMessage);
        break;
      case 'warn':
        this.logger.warn(jsonMessage);
        break;
      case 'error':
        this.logger.error(jsonMessage);
        break;
    }
  }
}
