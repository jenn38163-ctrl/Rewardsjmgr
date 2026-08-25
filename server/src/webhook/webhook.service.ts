import { Injectable, Logger, BadRequestException, ConflictException } from '@nestjs/common';
import { DbService } from '../db/db.service';
import { LedgerService } from '../ledger/ledger.service';
import * as schema from '../db/schema';
import { eq } from 'drizzle-orm';
import { encryptValue } from '../order/order.service';

export interface LocalWebhookEventDto {
  eventId: string;
  provider: 'ZENDIT' | 'GIFTBIT';
  eventType: 'order.fulfilled' | 'order.failed';
  timestamp: string;
  payload: {
    orderId: string;
    status: 'COMPLETED' | 'FAILED' | 'REFUNDED';
    voucherCode?: string;
    voucherPin?: string;
    failureReason?: string;
  };
}

@Injectable()
export class WebhookService {
  private readonly logger = new Logger(WebhookService.name);

  constructor(
    private readonly dbService: DbService,
    private readonly ledgerService: LedgerService
  ) {}

  async handleIncomingWebhook(event: LocalWebhookEventDto): Promise<any> {
    const { eventId, provider, eventType, payload } = event;
    this.logger.log(`Received Async Webhook Event=${eventId} from Provider=${provider}, EventType=${eventType}`);

    if (!this.dbService.db) {
      this.logger.warn(`Database not active. Simulating webhook ingestion.`);
      return { success: true };
    }

    // Step 1: Log event id atomically for webhook idempotency (FIN-06)
    try {
      await this.dbService.db.insert(schema.processedWebhooks).values({
        eventId,
        orderId: payload.orderId,
      });
    } catch (e) {
      this.logger.warn(`Webhook event ${eventId} already processed or processing. Skipping duplicate callback to avoid dual execution.`);
      return { success: true, message: 'Already processed (Idempotent).' };
    }

    // Step 2: Use pessimistic row lock (SELECT FOR UPDATE) to guard order state transition (FIN-05)
    try {
      return await this.dbService.db.transaction(async (tx) => {
        const [order] = await tx
          .select()
          .from(schema.gatewayOrders)
          .where(eq(schema.gatewayOrders.id, payload.orderId))
          .for('update');

        if (!order) {
          throw new BadRequestException(`Order ${payload.orderId} does not exist in local database.`);
        }

        // Guard against already processed/terminal orders to prevent dual reversals
        if (order.state === 'COMPLETED' || order.state === 'REFUNDED' || order.state === 'FAILED_TERMINAL') {
          this.logger.log(`Order ${order.id} is already in a terminal state (${order.state}). Skipping event processing.`);
          return { success: true, message: 'Order already terminal.' };
        }

        let nextState = 'COMPLETED';
        let refundTriggered = false;

        if (eventType === 'order.fulfilled' || payload.status === 'COMPLETED') {
          nextState = 'COMPLETED';
          this.logger.log(`Webhook marked Order=${order.id} as COMPLETED. Moving reserved funds to provider prepaid float.`);
          
          // Finalize Escrow: Move funds from LIABILITY:PENDING_DISPATCH escrow to Provider Prepaid Float (FIN-04)
          await this.ledgerService.finalizeFundsForOrder(
            order.id,
            provider,
            order.amountInr,
            `Webhook finalization payout`
          );
        } else {
          nextState = 'REFUNDED';
          refundTriggered = true;
          this.logger.error(`Webhook marked Order=${order.id} as FAILED. Triggering escrow reservation release back to user...`);
          
          // Release Escrow: Move funds from LIABILITY:PENDING_DISPATCH escrow back to User Wallet (FIN-04)
          await this.ledgerService.releaseReservationForOrder(
            order.id,
            order.userId,
            order.amountInr,
            `Webhook callback failure: ${payload.failureReason || 'unknown'}`
          );
        }

        const voucherCodeEncrypted = payload.voucherCode ? encryptValue(payload.voucherCode) : order.voucherCodeEncrypted;
        const voucherPinEncrypted = payload.voucherPin ? encryptValue(payload.voucherPin) : order.voucherPinEncrypted;

        await tx.update(schema.gatewayOrders)
          .set({
            state: nextState,
            voucherCodeEncrypted,
            voucherPinEncrypted,
            failureReason: payload.failureReason || order.failureReason,
            updatedAt: new Date(),
          })
          .where(eq(schema.gatewayOrders.id, order.id));

        return {
          success: true,
          orderId: order.id,
          state: nextState,
          refundTriggered,
        };
      });
    } catch (err) {
      this.logger.error(`Failed to securely process webhook: ${err.message}`);
      // Clean up processed webhooks entry on failure so they can retry callback delivery if appropriate
      try {
        await this.dbService.db.delete(schema.processedWebhooks).where(eq(schema.processedWebhooks.eventId, eventId));
      } catch (delErr) {
        this.logger.error(`Failed to rollback processed webhooks log: ${delErr.message}`);
      }
      throw err;
    }
  }
}

