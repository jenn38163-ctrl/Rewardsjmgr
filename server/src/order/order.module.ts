import { Module } from '@nestjs/common';
import { OrderService } from './order.service';
import { OrderController } from './order.controller';
import { LedgerModule } from '../ledger/ledger.module';
import { ProviderModule } from '../provider/provider.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [LedgerModule, ProviderModule, AuthModule],
  controllers: [OrderController],
  providers: [OrderService],
  exports: [OrderService],
})
export class OrderModule {}
