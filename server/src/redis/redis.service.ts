import { Injectable, OnModuleInit, OnModuleDestroy, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createClient, RedisClientType } from 'redis';
import { DbLogger, scrubSensitiveFields } from '../utils/dbLogger';

export interface RedisPingResult {
  ok: boolean;
  latencyMs: number;
  status: 'CONNECTED' | 'READY' | 'DISCONNECTED' | 'ERROR';
  error?: string;
}

@Injectable()
export class RedisService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RedisService.name);
  private client: RedisClientType | null = null;
  private isConnected = false;
  private connectionError: string | null = null;
  private lastPingLatencyMs = 0;

  constructor(private readonly configService: ConfigService) {}

  async onModuleInit(): Promise<void> {
    const redisUrl =
      this.configService.get<string>('REDIS_URL') ||
      process.env.REDIS_URL ||
      `redis://${process.env.REDIS_HOST || '127.0.0.1'}:${process.env.REDIS_PORT || '6379'}`;

    try {
      this.client = createClient({
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

      this.client.on('connect', () => {
        this.isConnected = true;
        this.connectionError = null;
        this.logger.log('Redis client connection established');
      });

      this.client.on('ready', () => {
        this.isConnected = true;
        this.connectionError = null;
        this.logger.log('Redis client is ready to accept commands');
      });

      this.client.on('error', (err) => {
        this.isConnected = false;
        this.connectionError = err?.message || 'Redis client connection error';
        this.logger.warn(`Redis connection warning: ${err?.message || err}`);
      });

      this.client.on('end', () => {
        this.isConnected = false;
        this.logger.log('Redis connection closed');
      });

      // Attempt to connect asynchronously without blocking application bootstrap if Redis is offline
      this.client.connect().catch((err) => {
        this.isConnected = false;
        this.connectionError = err?.message || 'Failed to connect to Redis server';
        this.logger.warn(`Initial Redis connection could not be established: ${err?.message || err}`);
      });
    } catch (error: any) {
      this.isConnected = false;
      this.connectionError = error?.message || 'Redis initialization error';
      this.logger.error(`Error initializing Redis client: ${error?.message || error}`);
    }
  }

  async onModuleDestroy(): Promise<void> {
    if (this.client) {
      try {
        await this.client.quit();
        this.logger.log('Redis connection gracefully closed');
      } catch (err: any) {
        this.logger.warn(`Error during Redis client shutdown: ${err?.message || err}`);
      }
    }
  }

  /**
   * Pings the Redis server and returns connection status, latency in milliseconds, and any error message.
   */
  async pingRedis(): Promise<RedisPingResult> {
    if (!this.client) {
      return {
        ok: false,
        latencyMs: 0,
        status: 'DISCONNECTED',
        error: this.connectionError || 'Redis client is not initialized',
      };
    }

    const start = process.hrtime.bigint();
    try {
      const response = await this.client.ping();
      const end = process.hrtime.bigint();
      const latencyMs = Number((Number(end - start) / 1_000_000).toFixed(2));
      this.lastPingLatencyMs = latencyMs;
      this.isConnected = true;
      this.connectionError = null;

      return {
        ok: response === 'PONG',
        latencyMs,
        status: 'CONNECTED',
      };
    } catch (error: any) {
      const end = process.hrtime.bigint();
      const latencyMs = Number((Number(end - start) / 1_000_000).toFixed(2));
      this.isConnected = false;
      this.connectionError = error?.message || 'Redis PING failed';

      return {
        ok: false,
        latencyMs,
        status: 'ERROR',
        error: error?.message || 'Failed to ping Redis',
      };
    }
  }

  /**
   * Returns true if the client is actively connected and responding.
   */
  isClientConnected(): boolean {
    return this.isConnected && !!this.client?.isOpen;
  }

  /**
   * Retrieves the raw Redis client instance if needed.
   */
  getClient(): RedisClientType | null {
    return this.client;
  }

  /**
   * Gets a value from Redis by key.
   */
  async get(key: string): Promise<string | null> {
    if (!this.client || !this.client.isOpen) return null;
    return this.client.get(key);
  }

  /**
   * Sets a value in Redis with optional TTL in seconds.
   */
  async set(key: string, value: string, ttlSeconds?: number): Promise<boolean> {
    if (!this.client || !this.client.isOpen) return false;
    if (ttlSeconds) {
      await this.client.set(key, value, { EX: ttlSeconds });
    } else {
      await this.client.set(key, value);
    }
    return true;
  }

  /**
   * Deletes a key from Redis.
   */
  async del(key: string): Promise<boolean> {
    if (!this.client || !this.client.isOpen) return false;
    const result = await this.client.del(key);
    return result > 0;
  }
}
