import express, { Application, Request, Response, NextFunction } from 'express';
import { Pool } from 'pg';
import { createClient, RedisClientType } from 'redis';
import { pool, getPoolMetrics } from './db';
import { DbLogger } from './utils/dbLogger';

/**
 * Singleton Redis client instance for the Express application.
 */
let redisClient: RedisClientType | null = null;

export function getRedisClient(): RedisClientType {
  if (!redisClient) {
    const redisUrl =
      process.env.REDIS_URL ||
      `redis://${process.env.REDIS_HOST || '127.0.0.1'}:${process.env.REDIS_PORT || '6379'}`;

    redisClient = createClient({
      url: redisUrl,
      socket: {
        connectTimeout: 5000,
        reconnectStrategy: (retries) => {
          if (retries > 10) {
            return new Error('Max Redis reconnection attempts reached');
          }
          return Math.min(retries * 100, 3000);
        },
      },
    });

    redisClient.on('connect', () => {
      console.log('[Express App] Redis client connected');
    });

    redisClient.on('error', (err) => {
      console.warn(`[Express App] Redis connection warning: ${err?.message || err}`);
    });

    redisClient.connect().catch((err) => {
      console.warn(`[Express App] Initial Redis connection could not be established: ${err?.message || err}`);
    });
  }
  return redisClient;
}

/**
 * Performs asynchronous health checks on both the PostgreSQL connection pool (via SELECT 1)
 * and Redis instance (via PING).
 */
export async function checkInfrastructureHealth(
  pgPool: Pool = pool,
  rClient: RedisClientType = getRedisClient()
): Promise<{
  isHealthy: boolean;
  database: { connected: boolean; latencyMs: number; error: string | null };
  redis: { connected: boolean; latencyMs: number; error: string | null };
}> {
  let dbHealthy = false;
  let dbLatencyMs = 0;
  let dbError: string | null = null;

  let redisHealthy = false;
  let redisLatencyMs = 0;
  let redisError: string | null = null;

  // 1. PostgreSQL check via SELECT 1
  const dbStart = process.hrtime.bigint();
  try {
    const client = await pgPool.connect();
    try {
      await client.query('SELECT 1 AS health_check');
      const dbEnd = process.hrtime.bigint();
      dbLatencyMs = Number((Number(dbEnd - dbStart) / 1_000_000).toFixed(2));
      dbHealthy = true;
    } finally {
      client.release();
    }
  } catch (err: any) {
    const dbEnd = process.hrtime.bigint();
    dbLatencyMs = Number((Number(dbEnd - dbStart) / 1_000_000).toFixed(2));
    dbError = err?.message || 'Database connection error';
    dbHealthy = false;
  }

  // 2. Redis check via PING
  const redisStart = process.hrtime.bigint();
  try {
    if (!rClient.isOpen) {
      await rClient.connect().catch(() => {});
    }
    const pong = await rClient.ping();
    const redisEnd = process.hrtime.bigint();
    redisLatencyMs = Number((Number(redisEnd - redisStart) / 1_000_000).toFixed(2));
    redisHealthy = pong === 'PONG' || (pong as any) === true;
  } catch (err: any) {
    const redisEnd = process.hrtime.bigint();
    redisLatencyMs = Number((Number(redisEnd - redisStart) / 1_000_000).toFixed(2));
    redisError = err?.message || 'Redis connection error';
    redisHealthy = false;
  }

  return {
    isHealthy: dbHealthy && redisHealthy,
    database: {
      connected: dbHealthy,
      latencyMs: dbLatencyMs,
      error: dbError,
    },
    redis: {
      connected: redisHealthy,
      latencyMs: redisLatencyMs,
      error: redisError,
    },
  };
}

/**
 * Creates and configures the Express application with /healthz endpoint.
 */
export function createApp(): Application {
  const app = express();

  // Middleware
  app.use(express.json());
  app.use(express.urlencoded({ extended: true }));

  // CORS headers
  app.use((req: Request, res: Response, next: NextFunction) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    if (req.method === 'OPTIONS') {
      return res.sendStatus(200);
    }
    next();
  });

  /**
   * GET /healthz
   * Returns 200 OK only if both PostgreSQL (via SELECT 1) and Redis (via PING) are responsive.
   * Returns 503 Service Unavailable if either check fails.
   */
  app.get('/healthz', async (req: Request, res: Response) => {
    const startTime = Date.now();
    const health = await checkInfrastructureHealth();
    const poolStats = getPoolMetrics();

    const statusCode = health.isHealthy ? 200 : 503;

    return res.status(statusCode).json({
      status: health.isHealthy ? 'OK' : 'UNHEALTHY',
      timestamp: new Date().toISOString(),
      service: 'rewards-gateway-backend',
      durationMs: Date.now() - startTime,
      database: {
        ...health.database,
        status: poolStats.waitingCount > 5 ? 'DEGRADED' : 'HEALTHY',
        pool: poolStats,
      },
      redis: {
        ...health.redis,
        status: health.redis.connected ? 'CONNECTED' : 'DISCONNECTED',
      },
    });
  });

  /**
   * GET /readyz - Kubernetes Readiness probe
   */
  app.get('/readyz', async (req: Request, res: Response) => {
    const health = await checkInfrastructureHealth();
    const statusCode = health.isHealthy ? 200 : 503;
    return res.status(statusCode).json({
      status: health.isHealthy ? 'READY' : 'NOT_READY',
      timestamp: new Date().toISOString(),
      service: 'rewards-gateway-backend',
      database: health.database,
      redis: health.redis,
    });
  });

  return app;
}

export const app: Application = createApp();
export default app;
