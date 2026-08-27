import { Module } from '@nestjs/common';
import { HealthController } from './health.controller';
import { DbModule } from '../db/db.module';
import { RedisModule } from '../redis/redis.module';

@Module({
  imports: [DbModule, RedisModule],
  controllers: [HealthController],
})
export class HealthModule {}

