import { Controller, Get, HttpStatus, Res, Logger } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { Response } from 'express';
import { DbService } from '../db/db.service';
import { RedisService } from '../redis/redis.service';

@ApiTags('Health & Observability')
@Controller()
export class HealthController {
  private readonly logger = new Logger(HealthController.name);

  constructor(
    private readonly dbService: DbService,
    private readonly redisService: RedisService,
  ) {}

  /**
   * Healthz endpoint for infrastructure readiness & liveness verification.
   * Returns 200 OK only when BOTH the PostgreSQL database and Redis connections are verified.
   * Returns 503 Service Unavailable if either connection fails verification.
   */
  @Get('healthz')
  @ApiOperation({
    summary: 'Infrastructure health probe verifying database and Redis connectivity',
    description: 'Returns 200 OK only when both PostgreSQL database pool and Redis instance connections are verified and responsive.',
  })
  @ApiResponse({ status: 200, description: 'Both database and Redis connections verified and responsive' })
  @ApiResponse({ status: 503, description: 'Database or Redis connection failed or degraded' })
  async getHealthz(@Res() res: Response) {
    const [dbPingResult, redisPingResult] = await Promise.all([
      this.dbService.pingDatabase(),
      this.redisService.pingRedis(),
    ]);

    const poolStats = this.dbService.getPoolStats();
    const isDbHealthy = dbPingResult.ok && poolStats.status !== 'DISCONNECTED';
    const isRedisHealthy = redisPingResult.ok;
    const isOverallHealthy = isDbHealthy && isRedisHealthy;

    const statusCode = isOverallHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

    return res.status(statusCode).json({
      status: isOverallHealthy ? 'OK' : 'UNHEALTHY',
      timestamp: new Date().toISOString(),
      service: 'rewards-gateway-backend',
      database: {
        connected: dbPingResult.ok,
        status: poolStats.status,
        latencyMs: dbPingResult.latencyMs,
        pool: poolStats,
        error: dbPingResult.error || null,
      },
      redis: {
        connected: redisPingResult.ok,
        status: redisPingResult.status,
        latencyMs: redisPingResult.latencyMs,
        error: redisPingResult.error || null,
      },
    });
  }

  /**
   * Readiness probe with PostgreSQL pool telemetry and Redis latency metrics.
   */
  @Get('readyz')
  @ApiOperation({ summary: 'Readiness probe with PostgreSQL pool telemetry and Redis latency stats' })
  @ApiResponse({ status: 200, description: 'PostgreSQL database pool and Redis are healthy and ready' })
  @ApiResponse({ status: 503, description: 'PostgreSQL or Redis connection degraded or disconnected' })
  async getReadiness(@Res() res: Response) {
    const [dbPingResult, redisPingResult] = await Promise.all([
      this.dbService.pingDatabase(),
      this.redisService.pingRedis(),
    ]);

    const poolStats = this.dbService.getPoolStats();
    const isDbHealthy = dbPingResult.ok && poolStats.status !== 'DISCONNECTED';
    const isRedisHealthy = redisPingResult.ok;
    const isReady = isDbHealthy && isRedisHealthy;

    const statusCode = isReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

    return res.status(statusCode).json({
      status: isReady ? 'READY' : 'NOT_READY',
      timestamp: new Date().toISOString(),
      service: 'rewards-gateway-backend',
      database: {
        connected: dbPingResult.ok,
        status: poolStats.status,
        latencyMs: dbPingResult.latencyMs,
        pool: poolStats,
        error: dbPingResult.error || null,
      },
      redis: {
        connected: redisPingResult.ok,
        status: redisPingResult.status,
        latencyMs: redisPingResult.latencyMs,
        error: redisPingResult.error || null,
      },
    });
  }
}
