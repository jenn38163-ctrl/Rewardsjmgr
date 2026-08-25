import { Module } from '@nestjs/common';
import { LedgerService } from './ledger.service';
import { DbModule } from '../db/db.module';

@Module({
  imports: [DbModule],
  providers: [LedgerService],
  exports: [LedgerService],
})
export class LedgerModule {}
