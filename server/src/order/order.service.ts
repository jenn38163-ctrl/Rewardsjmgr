import { Injectable, Logger, BadRequestException, NotFoundException, InternalServerErrorException } from '@nestjs/common';
import { DbService } from '../db/db.service';
import { LedgerService } from '../ledger/ledger.service';
import { ProviderService } from '../provider/provider.service';
import * as schema from '../db/schema';
import { eq, and } from 'drizzle-orm';
import { v4 as uuidv4 } from 'uuid';
import * as crypto from 'crypto';

const ENCRYPTION_SECRET = process.env.ENCRYPTION_SECRET || 'super_secret_ledger_key_2026_99';

export function encryptValue(text: string): string {
  if (!text) return text;
  try {
    const iv = crypto.randomBytes(16);
    const key = crypto.scryptSync(ENCRYPTION_SECRET, 'salt', 32);
    const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);
    let encrypted = cipher.update(text, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    return `${iv.toString('hex')}:${encrypted}`;
  } catch (e) {
    return text;
  }
}

export function decryptValue(encryptedText: string): string {
  if (!encryptedText) return encryptedText;
  try {
    const parts = encryptedText.split(':');
    if (parts.length !== 2) return encryptedText;
    const iv = Buffer.from(parts[0], 'hex');
    const encrypted = parts[1];
    const key = crypto.scryptSync(ENCRYPTION_SECRET, 'salt', 32);
    const decipher = crypto.createDecipheriv('aes-256-cbc', key, iv);
    let decrypted = decipher.update(encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
  } catch (e) {
    return encryptedText;
  }
}

export interface CreateOrderDto {
  userId: string;
  itemType: 'GIFT_CARD' | 'ESIM_PLAN' | 'MOBILE_TOPUP';
  title: string;
  amountInr: number;
  recipient: string;
  idempotencyKey: string;
}

@Injectable()
export class OrderService {
  private readonly logger = new Logger(OrderService.name);

  constructor(
    private readonly dbService: DbService,
    private readonly ledgerService: LedgerService,
    private readonly providerService: ProviderService
  ) {}

  /**
   * Create & Dispatch an Order Idempotently (FIN-03 & FIN-04 & FIN-02 & FIN-10)
   */
  async createOrder(dto: CreateOrderDto): Promise<any> {
    const { userId, itemType, title, amountInr, recipient, idempotencyKey } = dto;

    if (!this.dbService.db) {
      this.logger.warn(`Database not active. Simulating order placement: ${title}`);
      return {
        id: `sim-order-${uuidv4().substring(0, 8)}`,
        orderNumber: `ORD-${Date.now()}`,
        state: 'COMPLETED',
        title,
        amountInr,
        voucherCode: `MOCK-CLAIM-${uuidv4().substring(0, 6).toUpperCase()}`,
        voucherPin: '1234',
      };
    }

    // Step 1: Atomic Idempotency Acquisition (FIN-03)
    try {
      await this.dbService.db.insert(schema.idempotencyLocks).values({
        key: idempotencyKey,
        userId,
        lockedUntil: new Date(Date.now() + 60000), // Lock for 60 seconds
      });
    } catch (err) {
      // If insertion fails due to duplicate key, retrieve existing lock
      const existingLock = await this.dbService.db.query.idempotencyLocks.findFirst({
        where: (fields, { eq }) => eq(fields.key, idempotencyKey),
      });

      if (existingLock) {
        this.logger.log(`Returning idempotent response for key: ${idempotencyKey}`);
        if (existingLock.responsePayload) {
          return existingLock.responsePayload;
        }
        throw new BadRequestException('Order with this idempotency key is already processing.');
      }
      throw new BadRequestException('Idempotency lock violation.');
    }

    // Step 2: Isolated user wallet balance check before reservation (FIN-02)
    const userWalletBalance = await this.ledgerService.getUserWalletBalance(userId);
    if (userWalletBalance < amountInr) {
      // Clear idempotency lock so they can correct balance and retry
      await this.dbService.db.delete(schema.idempotencyLocks).where(eq(schema.idempotencyLocks.key, idempotencyKey));
      throw new BadRequestException(`Insufficient wallet balance. Current: ₹${userWalletBalance}, Required: ₹${amountInr}`);
    }

    const orderNumber = `ORD-${Date.now()}-${Math.floor(100 + Math.random() * 900)}`;
    const provider = itemType === 'GIFT_CARD' ? 'GIFTBIT' : 'ZENDIT';

    try {
      // Step 3: Insert initial order record in PENDING state
      const [order] = await this.dbService.db.insert(schema.gatewayOrders).values({
        orderNumber,
        userId,
        idempotencyKey,
        provider,
        itemType,
        title,
        amountInr,
        state: 'PENDING',
        recipientMasked: recipient,
        retryCount: 0,
        maxRetries: 3,
        lockVersion: 0,
      }).returning();

      // Step 4: Reserve Funds (FIN-04)
      // This moves user funds to LIABILITY:PENDING_DISPATCH escrow account
      await this.ledgerService.reserveFundsForOrder(
        order.id,
        userId,
        amountInr,
        `Escrow Hold: ${title} to ${recipient}`
      );

      // Transition order state: PENDING -> PROCESSING
      await this.dbService.db.update(schema.gatewayOrders)
        .set({ state: 'PROCESSING', updatedAt: new Date() })
        .where(eq(schema.gatewayOrders.id, order.id));

      // Step 5: Remote Call to Provider (Executed safely outside database transaction)
      const dispatchResult = await this.providerService.fulfillOrder(
        order.id,
        recipient,
        amountInr,
        provider as 'ZENDIT' | 'GIFTBIT',
        itemType
      );

      let finalState = 'COMPLETED';
      let voucherCode = null;
      let voucherPin = null;
      let failureReason = null;

      if (dispatchResult.success) {
        finalState = 'COMPLETED';
        voucherCode = dispatchResult.voucherCode;
        voucherPin = dispatchResult.voucherPin;

        // Move funds from escrow to provider prepaid float (INR)
        await this.ledgerService.finalizeFundsForOrder(
          order.id,
          provider as 'ZENDIT' | 'GIFTBIT',
          amountInr,
          `Payout: ${title}`
        );
      } else {
        if (dispatchResult.isRetryable) {
          finalState = 'FAILED_RETRYABLE';
          failureReason = dispatchResult.error;
          // Hold reservation in escrow (ready for retry or reconciliation)
        } else {
          finalState = 'FAILED_TERMINAL';
          failureReason = dispatchResult.error;

          // Release funds reservation back to user's wallet
          await this.ledgerService.releaseReservationForOrder(
            order.id,
            userId,
            amountInr,
            `Cancellation Reversal: ${dispatchResult.error}`
          );
          finalState = 'REFUNDED';
        }
      }

      // Step 6: Encrypt sensitive code fields & persist final outcome (FIN-10)
      const voucherCodeEncrypted = voucherCode ? encryptValue(voucherCode) : null;
      const voucherPinEncrypted = voucherPin ? encryptValue(voucherPin) : null;

      const [finalOrder] = await this.dbService.db.update(schema.gatewayOrders)
        .set({
          state: finalState,
          voucherCodeEncrypted,
          voucherPinEncrypted,
          failureReason,
          updatedAt: new Date()
        })
        .where(eq(schema.gatewayOrders.id, order.id))
        .returning();

      const responsePayload = {
        id: finalOrder.id,
        orderNumber: finalOrder.orderNumber,
        state: finalOrder.state,
        title: finalOrder.title,
        amountInr: finalOrder.amountInr,
        voucherCode: voucherCode || null,
        voucherPin: voucherPin || null,
        failureReason: finalOrder.failureReason,
      };

      // Save payload in idempotency lock table for future lookups
      await this.dbService.db.update(schema.idempotencyLocks)
        .set({ responsePayload })
        .where(eq(schema.idempotencyLocks.key, idempotencyKey));

      return responsePayload;

    } catch (e) {
      this.logger.error(`Critical order creation error: ${e.message}`);
      // Remove lock in case of early crashes before dispatch
      await this.dbService.db.delete(schema.idempotencyLocks).where(eq(schema.idempotencyLocks.key, idempotencyKey));
      throw new InternalServerErrorException(`Fulfillment engine failure: ${e.message}`);
    }
  }

  /**
   * Retry a Failed-Retryable Order with explicit ownership verification (FIN-08 & FIN-04)
   */
  async retryOrder(orderId: string, userId: string): Promise<any> {
    if (!this.dbService.db) {
      throw new BadRequestException('Database not configured.');
    }

    const order = await this.dbService.db.query.gatewayOrders.findFirst({
      where: (fields, { eq }) => eq(fields.id, orderId),
    });

    if (!order) {
      throw new NotFoundException(`Order ${orderId} not found.`);
    }

    // Enforce Ownership Verification (FIN-08)
    if (order.userId !== userId) {
      throw new BadRequestException('Access Denied: You do not own this order record.');
    }

    if (order.state !== 'FAILED_RETRYABLE') {
      throw new BadRequestException(`Order ${orderId} cannot be retried in state ${order.state}.`);
    }

    // Check retry bounds
    if (order.retryCount >= order.maxRetries) {
      // Transition to REFUNDED & Release escrow reservation back to user wallet
      await this.ledgerService.releaseReservationForOrder(
        order.id,
        userId,
        order.amountInr,
        `Exhausted retry limits (${order.maxRetries})`
      );
      
      const [updated] = await this.dbService.db.update(schema.gatewayOrders)
        .set({
          state: 'REFUNDED',
          failureReason: `Max retries (${order.maxRetries}) exhausted. Wallet reservation released.`,
          updatedAt: new Date()
        })
        .where(eq(schema.gatewayOrders.id, order.id))
        .returning();

      return updated;
    }

    // Increment retry count and set back to PROCESSING
    await this.dbService.db.update(schema.gatewayOrders)
      .set({
        state: 'PROCESSING',
        retryCount: order.retryCount + 1,
        updatedAt: new Date()
      })
      .where(eq(schema.gatewayOrders.id, order.id));

    this.logger.log(`Retrying Dispatch: Order=${order.id}, Attempt=${order.retryCount + 1}/${order.maxRetries}`);

    const dispatchResult = await this.providerService.fulfillOrder(
      order.id,
      order.recipientMasked,
      order.amountInr,
      order.provider as 'ZENDIT' | 'GIFTBIT',
      order.itemType as any
    );

    let finalState = 'COMPLETED';
    let voucherCode = null;
    let voucherPin = null;
    let failureReason = null;

    if (dispatchResult.success) {
      finalState = 'COMPLETED';
      voucherCode = dispatchResult.voucherCode;
      voucherPin = dispatchResult.voucherPin;

      // Finalize escrow to provider float INR (FIN-04)
      await this.ledgerService.finalizeFundsForOrder(
        order.id,
        order.provider as 'ZENDIT' | 'GIFTBIT',
        order.amountInr,
        `Payout on retry: ${order.title}`
      );
    } else {
      if (dispatchResult.isRetryable && order.retryCount + 1 < order.maxRetries) {
        finalState = 'FAILED_RETRYABLE';
        failureReason = dispatchResult.error;
      } else {
        finalState = 'REFUNDED';
        failureReason = `${dispatchResult.error}. Wallet reservation released.`;
        
        // Release reservation back to user wallet
        await this.ledgerService.releaseReservationForOrder(
          order.id,
          userId,
          order.amountInr,
          `Exhausted retry bounds on attempt: ${order.retryCount + 1}`
        );
      }
    }

    const voucherCodeEncrypted = voucherCode ? encryptValue(voucherCode) : null;
    const voucherPinEncrypted = voucherPin ? encryptValue(voucherPin) : null;

    const [finalOrder] = await this.dbService.db.update(schema.gatewayOrders)
      .set({
        state: finalState,
        voucherCodeEncrypted,
        voucherPinEncrypted,
        failureReason,
        updatedAt: new Date()
      })
      .where(eq(schema.gatewayOrders.id, order.id))
      .returning();

    return finalOrder;
  }

  /**
   * Get orders for a specific user with real-time voucher decrypted details
   */
  async getUserOrders(userId: string): Promise<any[]> {
    if (!this.dbService.db) return [];
    const orders = await this.dbService.db.query.gatewayOrders.findMany({
      where: (fields, { eq }) => eq(fields.userId, userId),
      orderBy: (fields, { desc }) => [desc(fields.createdAt)],
    });

    return orders.map(o => {
      return {
        ...o,
        voucherCode: o.voucherCodeEncrypted ? decryptValue(o.voucherCodeEncrypted) : null,
        voucherPin: o.voucherPinEncrypted ? decryptValue(o.voucherPinEncrypted) : null,
      };
    });
  }
}
