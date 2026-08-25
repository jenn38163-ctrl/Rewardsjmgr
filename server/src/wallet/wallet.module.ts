import { Module } from '@nestjs/common';
import { WalletService } from './wallet.service';
import { WalletController } from './wallet.controller';
import { LedgerModule } from '../ledger/ledger.module';
import { AuthModule } from '../auth/auth.module';
import { DbModule } from '../db/db.module';

@Module({
  imports: [LedgerModule, AuthModule, DbModule],
  controllers: [WalletController],
  providers: [WalletService],
})
export class WalletModule {}
