import { Pool, PoolClient } from 'pg';
import {
  sanitizeLogObject,
  sanitizeLogMessage,
  sanitizeConnectionUri,
  maskSensitiveFields,
  sanitizeLog,
  createSanitizedLogger,
  SENSITIVE_KEY_PATTERNS,
} from './logSanitizer';

export {
  sanitizeLogObject,
  sanitizeLogMessage,
  sanitizeConnectionUri,
  maskSensitiveFields,
  sanitizeLog,
  createSanitizedLogger,
};

/**
 * Dedicated list of sensitive field names to scrub from log entries and query payloads.
 * Includes voucher numbers, user IDs, authentication tokens, credentials, and financial identifiers.
 */
export const SCRUBBED_FIELDS = [
  // Voucher Numbers & Gift Cards
  'vouchernumber',
  'voucher_number',
  'vouchercode',
  'voucher_code',
  'voucher',
  'voucherpin',
  'voucher_pin',
  'claimcode',
  'claim_code',
  'claimpin',
  'claim_pin',
  'cardnumber',
  'card_number',
  'giftcardcode',
  'gift_card_code',
  'giftcard',
  'gift_card',
  'pin',
  'cvv',
  'cvc',

  // User Identifiers & Customer Identifiers
  'userid',
  'user_id',
  'uid',
  'customerid',
  'customer_id',
  'customertoken',
  'customer_token',
  'sub',
  'accountid',
  'account_id',

  // Auth Tokens, Passwords & Credentials
  'authtoken',
  'auth_token',
  'token',
  'accesstoken',
  'access_token',
  'refreshtoken',
  'refresh_token',
  'sessiontoken',
  'session_token',
  'idtoken',
  'id_token',
  'jwt',
  'bearer',
  'password',
  'passwd',
  'pass',
  'pwd',
  'secret',
  'apikey',
  'api_key',
  'key',
  'auth',
  'authorization',
  'credential',
  'credentials',
  'privatekey',
  'private_key',
  'signature',

  // Personally Identifiable Information (PII)
  'email',
  'phone',
  'phonenumber',
  'phone_number',
  'mobile',
  'mobilenumber',
  'ssn',
  'aadhaar',
  'aadhar',
  'pan',
  'pancard',
  'passport',
  'address',
];

/**
 * Helper function that deeply scrubs sensitive fields like voucher numbers,
 * user IDs, and auth tokens from log objects before they are emitted to the stdout stream.
 *
 * @param obj The object, array, or primitive value to sanitize.
 * @param visited Set used internally to prevent circular reference recursion.
 * @returns Deeply scrubbed copy of the input value.
 */
export function scrubSensitiveFields<T = any>(obj: T, visited = new WeakSet()): T {
  if (obj === null || obj === undefined) {
    return obj;
  }

  // Sanitize strings for inline URIs, Bearer tokens, emails, voucher codes, or JSON substrings
  if (typeof obj === 'string') {
    return sanitizeLogMessage(obj) as unknown as T;
  }

  if (typeof obj !== 'object') {
    return obj;
  }

  if (obj instanceof Date || obj instanceof RegExp) {
    return obj;
  }

  // Handle Error instances safely
  if (obj instanceof Error) {
    const errorObj: Record<string, any> = {
      name: obj.name,
      message: sanitizeLogMessage(obj.message),
      stack: obj.stack ? sanitizeLogMessage(obj.stack) : undefined,
    };
    for (const key of Object.keys(obj)) {
      if (!['name', 'message', 'stack'].includes(key)) {
        errorObj[key] = scrubSensitiveFields((obj as any)[key], visited);
      }
    }
    return errorObj as unknown as T;
  }

  // Prevent circular reference loops
  if (visited.has(obj as object)) {
    return '[CIRCULAR_REF]' as unknown as T;
  }
  visited.add(obj as object);

  // Handle Arrays
  if (Array.isArray(obj)) {
    return obj.map((item) => scrubSensitiveFields(item, visited)) as unknown as T;
  }

  // Handle Objects
  const scrubbed: Record<string, any> = {};

  for (const [key, value] of Object.entries(obj)) {
    const normalizedKey = key.toLowerCase().replace(/[^a-z0-9]/g, '');

    const isMatch = SCRUBBED_FIELDS.some((pattern) => {
      const cleanPattern = pattern.toLowerCase().replace(/[^a-z0-9]/g, '');
      return normalizedKey === cleanPattern || normalizedKey.includes(cleanPattern);
    });

    if (isMatch) {
      if (typeof value === 'string') {
        if (normalizedKey.includes('email')) {
          const parts = value.split('@');
          if (parts.length === 2) {
            const [local, domain] = parts;
            scrubbed[key] = local.length <= 2 ? `${local[0] || '*'}***@${domain}` : `${local[0]}***${local[local.length - 1]}@${domain}`;
          } else {
            scrubbed[key] = '*****';
          }
        } else if (normalizedKey.includes('card') || normalizedKey.includes('voucher') || normalizedKey.includes('claim')) {
          const digits = value.replace(/[\s-]/g, '');
          scrubbed[key] = digits.length >= 4 ? `****-****-****-${digits.slice(-4)}` : '*****';
        } else if (normalizedKey.includes('user') || normalizedKey.includes('uid') || normalizedKey.includes('customer')) {
          scrubbed[key] = '[REDACTED_USER_ID]';
        } else if (normalizedKey.includes('token') || normalizedKey.includes('auth') || normalizedKey.includes('jwt') || normalizedKey.includes('key')) {
          scrubbed[key] = '[REDACTED_TOKEN]';
        } else {
          scrubbed[key] = '*****';
        }
      } else if (typeof value === 'number') {
        if (normalizedKey.includes('user') || normalizedKey.includes('uid') || normalizedKey.includes('customer')) {
          scrubbed[key] = '[REDACTED_USER_ID]';
        } else {
          scrubbed[key] = '*****';
        }
      } else {
        scrubbed[key] = '*****';
      }
    } else if (typeof value === 'string') {
      scrubbed[key] = sanitizeLogMessage(value);
    } else if (typeof value === 'object' && value !== null) {
      scrubbed[key] = scrubSensitiveFields(value, visited);
    } else {
      scrubbed[key] = value;
    }
  }

  return scrubbed as unknown as T;
}

export interface DbPoolStatus {
  totalCount: number;
  idleCount: number;
  waitingCount: number;
  queueDepth: number;
  maxCapacity: number;
  activeCount: number;
  utilizationPercent: number;
}

export interface StructuredDbLogEntry {
  timestamp: string;
  source: string;
  event: string;
  message: string;
  level: 'INFO' | 'DEBUG' | 'WARN' | 'ERROR';
  poolStatus: DbPoolStatus;
  clientId?: number;
  latencyMs?: number;
  queueDepth?: number;
  error?: string;
  errorCode?: string;
  details?: Record<string, any>;
}

/**
 * Utility class to instrument pg.Pool with structured JSON logging to stdout.
 */
export class DbLogger {
  private static clientIdSequence = 0;
  private static clientMetadata = new WeakMap<PoolClient, { clientId: number; connectedAt: number; acquiredAt?: number }>();

  /**
   * Helper function on DbLogger to scrub sensitive fields like voucher numbers,
   * user IDs, and auth tokens from log objects.
   */
  public static scrubSensitiveFields<T = any>(obj: T): T {
    return scrubSensitiveFields(obj);
  }

  /**
   * Outputs structured JSON log directly to stdout stream with complete PII,
   * voucher number, user ID, auth token, and credential sanitization.
   */
  public static log(entry: Omit<StructuredDbLogEntry, 'timestamp' | 'source'>): void {
    const fullLog: StructuredDbLogEntry = {
      timestamp: new Date().toISOString(),
      source: 'DbLogger',
      queueDepth: entry.poolStatus?.queueDepth ?? entry.poolStatus?.waitingCount ?? 0,
      ...entry,
    };

    // Deeply scrub sensitive fields before emitting to the stdout stream
    const sanitizedLog = scrubSensitiveFields(fullLog);
    const jsonOutput = JSON.stringify(sanitizedLog);

    if (entry.level === 'ERROR') {
      console.error(jsonOutput);
    } else if (entry.level === 'WARN') {
      console.warn(jsonOutput);
    } else {
      console.log(jsonOutput);
    }
  }

  /**
   * Calculates current pool status snapshot from pg.Pool instance including queue depth and usage.
   */
  public static getPoolStatus(pool: Pool): DbPoolStatus {
    const totalCount = pool.totalCount || 0;
    const idleCount = pool.idleCount || 0;
    const waitingCount = pool.waitingCount || 0;
    const queueDepth = waitingCount;
    const maxCapacity = (pool as any).options?.max || 20;
    const activeCount = Math.max(0, totalCount - idleCount);
    const utilizationPercent = maxCapacity > 0 ? Number(((activeCount / maxCapacity) * 100).toFixed(1)) : 0;

    return {
      totalCount,
      idleCount,
      waitingCount,
      queueDepth,
      maxCapacity,
      activeCount,
      utilizationPercent,
    };
  }

  /**
   * Attaches event listeners ('connect', 'acquire', 'remove', 'release', 'error') to the pg pool instance
   * to log structured metrics like checkout latency, pool usage, and queue depth, ensuring that no PII,
   * user IDs, voucher numbers, or sensitive credentials are included in the logged objects.
   */
  public static monitorPool(pool: Pool, poolName = 'primary_pg_pool'): void {
    const initialStatus = DbLogger.getPoolStatus(pool);

    DbLogger.log({
      event: 'pg_pool_monitor_attached',
      message: `Database connection pool monitoring enabled for [${poolName}]`,
      level: 'INFO',
      poolStatus: initialStatus,
      queueDepth: initialStatus.queueDepth,
      details: { poolName },
    });

    // 1. 'connect' event listener: Emitted when a new physical connection is created in the pool
    pool.on('connect', (client: PoolClient) => {
      DbLogger.clientIdSequence++;
      const clientId = DbLogger.clientIdSequence;
      const connectedAt = Date.now();

      DbLogger.clientMetadata.set(client, {
        clientId,
        connectedAt,
      });

      const status = DbLogger.getPoolStatus(pool);

      DbLogger.log({
        event: 'pg_client_connect',
        message: `Physical connection established to PostgreSQL server (Client ID: ${clientId}, Pool Size: ${status.totalCount}/${status.maxCapacity}, Queue Depth: ${status.queueDepth})`,
        level: 'DEBUG',
        clientId,
        poolStatus: status,
        queueDepth: status.queueDepth,
      });
    });

    // 2. 'acquire' event listener: Emitted when a client is acquired / checked out from the pool
    pool.on('acquire', (client: PoolClient) => {
      const now = Date.now();
      const meta = DbLogger.clientMetadata.get(client) || { clientId: 0, connectedAt: now };
      meta.acquiredAt = now;
      DbLogger.clientMetadata.set(client, meta);

      const status = DbLogger.getPoolStatus(pool);

      DbLogger.log({
        event: 'pg_client_acquire',
        message: `Connection acquired from pool (Client ID: ${meta.clientId}, Active: ${status.activeCount}/${status.maxCapacity}, Queue Depth: ${status.queueDepth}, Util: ${status.utilizationPercent}%)`,
        level: 'DEBUG',
        clientId: meta.clientId,
        poolStatus: status,
        queueDepth: status.queueDepth,
      });
    });

    // 3. 'remove' event listener: Emitted when a client connection is closed and removed from the pool
    pool.on('remove', (client: PoolClient) => {
      const meta = DbLogger.clientMetadata.get(client);
      const totalLifetimeMs = meta ? Date.now() - meta.connectedAt : 0;
      const status = DbLogger.getPoolStatus(pool);

      DbLogger.log({
        event: 'pg_client_remove',
        message: `Connection closed and removed from pool (Client ID: ${meta?.clientId ?? 'unknown'}, Total Lifetime: ${totalLifetimeMs}ms, Remaining: ${status.totalCount})`,
        level: 'DEBUG',
        clientId: meta?.clientId,
        latencyMs: totalLifetimeMs,
        poolStatus: status,
        queueDepth: status.queueDepth,
        details: { lifetimeMs: totalLifetimeMs },
      });
    });

    // 4. 'release' event listener: Emitted when a client connection is released back to the idle pool
    pool.on('release', (err: Error | undefined, client: PoolClient) => {
      const meta = DbLogger.clientMetadata.get(client);
      const holdDurationMs = meta?.acquiredAt ? Date.now() - meta.acquiredAt : 0;
      const status = DbLogger.getPoolStatus(pool);

      if (err) {
        DbLogger.log({
          event: 'pg_client_release_error',
          message: `Error encountered while releasing connection to pool: ${err.message}`,
          level: 'WARN',
          clientId: meta?.clientId,
          error: err.message,
          latencyMs: holdDurationMs,
          poolStatus: status,
          queueDepth: status.queueDepth,
        });
      } else {
        DbLogger.log({
          event: 'pg_client_release',
          message: `Connection returned to pool (Client ID: ${meta?.clientId ?? 'unknown'}, Hold: ${holdDurationMs}ms, Idle: ${status.idleCount})`,
          level: 'DEBUG',
          clientId: meta?.clientId,
          latencyMs: holdDurationMs,
          poolStatus: status,
          queueDepth: status.queueDepth,
        });
      }
    });

    // 5. 'error' event listener: Emitted on unexpected errors from idle clients in the pool
    pool.on('error', (err: Error & { code?: string }, client?: PoolClient) => {
      const meta = client ? DbLogger.clientMetadata.get(client) : undefined;
      const status = DbLogger.getPoolStatus(pool);

      DbLogger.log({
        event: 'pg_pool_idle_error',
        message: `Unexpected idle client error in PostgreSQL pool: ${err.message}`,
        level: 'ERROR',
        clientId: meta?.clientId,
        error: err.message,
        errorCode: err.code || 'PG_IDLE_CLIENT_ERROR',
        poolStatus: status,
        queueDepth: status.queueDepth,
        details: { stack: err.stack },
      });
    });
  }

  /**
   * Initializes event listeners on a pg.Pool instance (alias for monitorPool).
   */
  public static initPoolLogger(pool: Pool, poolName = 'primary_pg_pool'): void {
    DbLogger.monitorPool(pool, poolName);
  }

  /**
   * Explicitly logs connection checkout latency or wait times.
   */
  public static logCheckoutLatency(pool: Pool, waitMs: number, operationName: string): void {
    const status = DbLogger.getPoolStatus(pool);
    if (waitMs > 100) {
      DbLogger.log({
        event: 'pg_connection_checkout_slow',
        message: `Elevated connection checkout wait time (${waitMs.toFixed(2)}ms) for '${operationName}' (Queue Depth: ${status.queueDepth})`,
        level: 'WARN',
        latencyMs: Number(waitMs.toFixed(2)),
        poolStatus: status,
        queueDepth: status.queueDepth,
        details: { operationName },
      });
    }
  }

  /**
   * Explicitly logs checkout and connection failures.
   */
  public static logCheckoutFailure(pool: Pool, error: Error & { code?: string }, waitMs: number, operationName: string): void {
    const status = DbLogger.getPoolStatus(pool);
    DbLogger.log({
      event: 'pg_connection_checkout_failure',
      message: `Failed to acquire client from pool for operation '${operationName}': ${error.message}`,
      level: 'ERROR',
      latencyMs: Number(waitMs.toFixed(2)),
      error: error.message,
      errorCode: error.code || 'CHECKOUT_FAILED',
      poolStatus: status,
      queueDepth: status.queueDepth,
      details: {
        operationName,
        stack: error.stack,
      },
    });
  }
}

/**
 * Attaches event listeners ('connect', 'acquire', 'remove') to the provided pg.Pool instance
 * to log structured, sanitized metrics (checkout latency, pool status, and queue depth) to stdout
 * while ensuring no PII, user IDs, voucher numbers, or sensitive credentials are ever logged.
 *
 * @param pool The pg.Pool instance to monitor.
 * @param poolName Optional identifier name for the pool.
 */
export function monitorPool(pool: Pool, poolName = 'main_database_pool'): void {
  DbLogger.monitorPool(pool, poolName);
}

/**
 * Convenience alias for monitorPool.
 */
export function registerPoolListener(pool: Pool, poolName = 'main_database_pool'): void {
  DbLogger.monitorPool(pool, poolName);
}

export const initDbPoolLogger = registerPoolListener;

/**
 * Object export containing the monitorPool(pool: Pool) function along with helper methods.
 */
export const dbLogger = {
  monitorPool,
  registerPoolListener,
  initPoolLogger: DbLogger.initPoolLogger,
  log: DbLogger.log,
  scrubSensitiveFields,
  getPoolStatus: DbLogger.getPoolStatus,
  logCheckoutLatency: DbLogger.logCheckoutLatency,
  logCheckoutFailure: DbLogger.logCheckoutFailure,
};

export default monitorPool;
