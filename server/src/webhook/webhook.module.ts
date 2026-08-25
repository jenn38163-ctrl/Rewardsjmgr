import { Module } from '@nestjs/common';
import { WebhookService } from './webhook.service';
import { WebhookController } from './webhook.controller';
import { LedgerModule } from '../ledger/ledger.module';
import { DbModule } from '../db/db.module';

@Module({
  imports: [LedgerModule, DbModule],
  controllers: [WebhookController],
  providers: [WebhookService],
})
export class WebhookModule {}
