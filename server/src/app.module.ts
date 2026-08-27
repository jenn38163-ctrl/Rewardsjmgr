import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { DbModule } from './db/db.module';
import { AuthModule } from './auth/auth.module';
import { LedgerModule } from './ledger/ledger.module';
import { ProviderModule } from './provider/provider.module';
import { OrderModule } from './order/order.module';
import { WalletModule } from './wallet/wallet.module';
import { WebhookModule } from './webhook/webhook.module';
import { HealthModule } from './health/health.module';
import { RedisModule } from './redis/redis.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env', '.env.example'],
    }),
    DbModule,
    RedisModule,
    HealthModule,
    AuthModule,
    LedgerModule,
    ProviderModule,
    OrderModule,
    WalletModule,
    WebhookModule,
  ],
})
export class AppModule {}

