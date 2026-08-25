import { Injectable, Logger, BadRequestException, InternalServerErrorException } from '@nestjs/common';
import { DbService } from '../db/db.service';
import * as schema from '../db/schema';
import { eq, sql } from 'drizzle-orm';
import { v4 as uuidv4 } from 'uuid';

export interface LedgerEntryDto {
  accountCode: string;
  amountCents: bigint;
  direction: 'DEBIT' | 'CREDIT';
  currency?: string;
}

@Injectable()
export class LedgerService {
  private readonly logger = new Logger(LedgerService.name);
  private readonly memoryIdempotencyLocks = new Set<String>();

  constructor(private dbService: DbService) {}

  /**
   * Executes a robust atomic Double-Entry transaction on PostgreSQL
   */
  async postTransaction(
    referenceType: string,
    referenceId: string,
    description: string,
    entries: LedgerEntryDto[],
    correlationId: string = uuidv4()
  ): Promise<string> {
    // 1. Strict Balance Verification
    const debits = entries.filter(e => e.direction === 'DEBIT').reduce((acc, e) => acc + e.amountCents, 0n);
    const credits = entries.filter(e => e.direction === 'CREDIT').reduce((acc, e) => acc + e.amountCents, 0n);

    if (debits !== credits || debits === 0n) {
      throw new BadRequestException(`Unbalanced Transaction! Debits (${debits}) must equal Credits (${credits}) and exceed zero.`);
    }

    if (!this.dbService.db) {
      this.logger.warn(`Database not initialized. Posting simulated transaction: ${description}`);
      return `SIM-TX-${uuidv4().take(8)}`;
    }

    try {
      return await this.dbService.db.transaction(async (tx) => {
        // Step 1: Write Journal Entry Header
        const [journal] = await tx.insert(schema.journalEntries).values({
          correlationId,
          referenceType,
          referenceId,
          description,
          metadata: {},
        }).returning();

        // Step 2: Handle entries sequentially to maintain strict ordering & lock records
        for (const entry of entries) {
          // Pessimistic Locking: Select account FOR UPDATE to serialize parallel balance modifications
          const [account] = await tx
            .select()
            .from(schema.accounts)
            .where(eq(schema.accounts.code, entry.accountCode))
            .for('update');

          if (!account) {
            throw new BadRequestException(`Chart of Account code ${entry.accountCode} does not exist.`);
          }

          // Step 3: Insert Line Item Entry
          await tx.insert(schema.ledgerEntries).values({
            journalId: journal.id,
            accountId: account.id,
            amountCents: entry.amountCents,
            direction: entry.direction,
            currency: entry.currency || 'INR',
          });

          // Step 4: Adjust Balance based on debit/credit rules
          // For Asset Accounts (Cash, Float): Debit increases, Credit decreases.
          // For Liability Accounts (User Wallets): Credit increases, Debit decreases.
          let balanceDelta = 0n;
          if (account.type === 'ASSET') {
            balanceDelta = entry.direction === 'DEBIT' ? entry.amountCents : -entry.amountCents;
          } else if (account.type === 'LIABILITY') {
            balanceDelta = entry.direction === 'CREDIT' ? entry.amountCents : -entry.amountCents;
          } else if (account.type === 'REVENUE') {
            balanceDelta = entry.direction === 'CREDIT' ? entry.amountCents : -entry.amountCents;
          } else if (account.type === 'EXPENSE') {
            balanceDelta = entry.direction === 'DEBIT' ? entry.amountCents : -entry.amountCents;
          }

          // Update Account Balance with Delta
          await tx
            .update(schema.accounts)
            .set({
              balanceCents: sql`${schema.accounts.balanceCents} + ${balanceDelta}`,
              updatedAt: new Date()
            })
            .where(eq(schema.accounts.code, entry.accountCode));
        }

        this.logger.log(`Posted Journal ${journal.id} (${description}) Successfully.`);
        return journal.id;
      });
    } catch (error) {
      this.logger.error(`Failed to post double entry transaction: ${error.message}`);
      throw new InternalServerErrorException(`Double entry journal failed: ${error.message}`);
    }
  }

  /**
   * Helper to ensure a thread-safe user wallet account exists in the Chart of Accounts
   */
  async getOrCreateUserWallet(userId: string): Promise<string> {
    const accountCode = `LIABILITY:USER_WALLET:${userId}`;
    if (!this.dbService.db) return accountCode;

    try {
      await this.dbService.db.transaction(async (tx) => {
        const [account] = await tx
          .select()
          .from(schema.accounts)
          .where(eq(schema.accounts.code, accountCode))
          .for('update');

        if (!account) {
          await tx.insert(schema.accounts).values({
            code: accountCode,
            name: `Isolated Wallet for User ${userId}`,
            type: 'LIABILITY',
            currency: 'INR',
            balanceCents: 500000n, // Seed with ₹5,000.00
            isActive: true,
          });
          this.logger.log(`Created isolated user liability wallet for: ${userId}`);
        }
      });
    } catch (error) {
      this.logger.warn(`Bypassed concurrent creation for user wallet (already created): ${error.message}`);
    }

    return accountCode;
  }

  /**
   * Step 1 of FIN-04: Reserve funds in Escrow (PENDING state) before hitting the remote provider API
   */
  async reserveFundsForOrder(
    orderId: string,
    userId: string,
    amountInr: number,
    description: string
  ): Promise<string> {
    const userWalletCode = await this.getOrCreateUserWallet(userId);
    const amountCents = BigInt(amountInr) * 100n;

    const entries: LedgerEntryDto[] = [
      { accountCode: userWalletCode, amountCents, direction: 'DEBIT' }, // Debits user wallet (decreases liability balance)
      { accountCode: 'LIABILITY:PENDING_DISPATCH', amountCents, direction: 'CREDIT' } // Credits escrow (increases liability balance)
    ];

    return await this.postTransaction(
      'ORDER_RESERVATION',
      orderId,
      `Reservation: ${description}`,
      entries
    );
  }

  /**
   * Step 2 of FIN-04: Move reserved escrow funds to provider float once fulfillment succeeds (COMPLETED state)
   */
  async finalizeFundsForOrder(
    orderId: string,
    provider: 'ZENDIT' | 'GIFTBIT',
    amountInr: number,
    description: string
  ): Promise<string> {
    const providerAccount = provider === 'GIFTBIT' ? 'ASSET:GIFTBIT_PREPAID_INR' : 'ASSET:ZENDIT_PREPAID_INR';
    const amountCents = BigInt(amountInr) * 100n;

    const entries: LedgerEntryDto[] = [
      { accountCode: 'LIABILITY:PENDING_DISPATCH', amountCents, direction: 'DEBIT' }, // Debits escrow (decreases liability balance)
      { accountCode: providerAccount, amountCents, direction: 'CREDIT' } // Credits provider float asset (decreases prepaid cash asset to pay provider)
    ];

    const result = await this.postTransaction(
      'Fulfillment_FINALIZATION',
      orderId,
      `Finalization: ${description} via ${provider}`,
      entries
    );

    // Trigger automatic float sweep in background
    this.checkAndTriggerAutoReplenish(provider).catch(e => 
      this.logger.error(`Background auto replenishment sweep failed: ${e.message}`)
    );

    return result;
  }

  /**
   * Step 3 of FIN-04: Release escrow reservation back to user wallet upon terminal failure (FAILED_TERMINAL / REFUNDED state)
   */
  async releaseReservationForOrder(
    orderId: string,
    userId: string,
    amountInr: number,
    description: string
  ): Promise<string> {
    const userWalletCode = await this.getOrCreateUserWallet(userId);
    const amountCents = BigInt(amountInr) * 100n;

    const entries: LedgerEntryDto[] = [
      { accountCode: 'LIABILITY:PENDING_DISPATCH', amountCents, direction: 'DEBIT' }, // Debits escrow (decreases liability balance)
      { accountCode: userWalletCode, amountCents, direction: 'CREDIT' } // Credits user wallet liability (increases balance)
    ];

    return await this.postTransaction(
      'RESERVATION_RELEASE',
      orderId,
      `Reversal/Refund: ${description}`,
      entries
    );
  }

  /**
   * Safe Order Purchase: Debits User Wallet liability, Credits downstream provider float asset (Direct)
   */
  async recordOrderPurchase(
    orderId: string,
    amountInr: number,
    provider: 'ZENDIT' | 'GIFTBIT',
    itemTitle: string,
    idempotencyKey: string
  ): Promise<boolean> {
    const memoryKey = `order:${idempotencyKey}`;
    
    // Idempotency check
    if (this.memoryIdempotencyLocks.has(memoryKey)) {
      this.logger.warn(`Idempotent block triggered for key: ${idempotencyKey}`);
      return false;
    }
    this.memoryIdempotencyLocks.add(memoryKey);

    try {
      const amountCents = BigInt(amountInr) * 100n;
      const providerAccount = provider === 'GIFTBIT' ? 'ASSET:GIFTBIT_PREPAID_INR' : 'ASSET:ZENDIT_PREPAID_INR';

      const entries: LedgerEntryDto[] = [
        { accountCode: 'LIABILITY:USER_WALLETS', amountCents, direction: 'DEBIT' },
        { accountCode: providerAccount, amountCents, direction: 'CREDIT' }
      ];

      await this.postTransaction(
        'PURCHASE_ORDER',
        orderId,
        `Fulfillment: ${itemTitle} via ${provider}`,
        entries
      );

      // Trigger automatic float health sweeps in the background
      this.checkAndTriggerAutoReplenish(provider).catch(e => 
        this.logger.error(`Bg auto replenishment sweep failed: ${e.message}`)
      );

      return true;
    } finally {
      this.memoryIdempotencyLocks.delete(memoryKey);
    }
  }

  /**
   * Wallet Top Up: Debits System Operating Cash, Credits User Wallet Balance (Isolated)
   */
  async recordWalletTopup(userId: string, amountInr: number): Promise<string> {
    const userWalletCode = await this.getOrCreateUserWallet(userId);
    const amountCents = BigInt(amountInr) * 100n;
    const entries: LedgerEntryDto[] = [
      { accountCode: 'ASSET:CASH_FLOAT_INR', amountCents, direction: 'DEBIT' },
      { accountCode: userWalletCode, amountCents, direction: 'CREDIT' }
    ];

    return await this.postTransaction(
      'WALLET_TOPUP',
      userId,
      `User ${userId} Top-Up ₹${amountInr}`,
      entries
    );
  }

  /**
   * Order Refund: Debits Downstream Provider Float Asset, Credits User Wallet Balance (Isolated)
   */
  async recordOrderRefund(orderId: string, userId: string, amountInr: number, provider: 'ZENDIT' | 'GIFTBIT'): Promise<string> {
    const userWalletCode = await this.getOrCreateUserWallet(userId);
    const amountCents = BigInt(amountInr) * 100n;
    const providerAccount = provider === 'GIFTBIT' ? 'ASSET:GIFTBIT_PREPAID_INR' : 'ASSET:ZENDIT_PREPAID_INR';

    const entries: LedgerEntryDto[] = [
      { accountCode: providerAccount, amountCents, direction: 'DEBIT' },
      { accountCode: userWalletCode, amountCents, direction: 'CREDIT' }
    ];

    return await this.postTransaction(
      'GATEWAY_REFUND',
      orderId,
      `Refund for Order ${orderId} (${provider})`,
      entries
    );
  }

  /**
   * Auto-Replenishment Threshold Sweep
   */
  async checkAndTriggerAutoReplenish(provider: 'ZENDIT' | 'GIFTBIT'): Promise<boolean> {
    if (!this.dbService.db) return false;

    const providerAccount = provider === 'GIFTBIT' ? 'ASSET:GIFTBIT_PREPAID_INR' : 'ASSET:ZENDIT_PREPAID_INR';
    
    // Generate an hourly bucket idempotent key to ensure workers only sweep once per hour
    const dateBucket = new Date().toISOString().substring(0, 13); // e.g. "2026-08-25T14"
    const sweepLockKey = `SWEEP_LOCK:${provider}:${dateBucket}`;

    try {
      await this.dbService.db.insert(schema.idempotencyLocks).values({
        key: sweepLockKey,
        userId: 'SYSTEM_AUTOSWEEP',
        lockedUntil: new Date(Date.now() + 3600000), // Lock for 1 hour
      });
    } catch (e) {
      // Lock already exists, sweep already performed this hour
      return false;
    }

    try {
      const account = await this.dbService.db.query.accounts.findFirst({
        where: (fields, { eq }) => eq(fields.code, providerAccount),
      });

      if (!account) return false;

      const thresholdCents = 1000000n; // ₹10,000 threshold
      const replenishChunkCents = 5000000n; // ₹50,000 recharge chunk

      if (account.balanceCents < thresholdCents) {
        this.logger.warn(`Provider Float for ${provider} under limit (${account.balanceCents} cents). Triggering auto-replenish sweep...`);
        
        const entries: LedgerEntryDto[] = [
          { accountCode: 'ASSET:CASH_FLOAT_INR', amountCents: replenishChunkCents, direction: 'DEBIT' },
          { accountCode: providerAccount, amountCents: replenishChunkCents, direction: 'CREDIT' }
        ];

        await this.postTransaction(
          'AUTO_REPLENISH',
          provider,
          `Auto Float Sweep: Top up ${providerAccount} from cash reserve`,
          entries
        );

        return true;
      }
    } catch (err) {
      this.logger.error(`Failed to execute auto-replenish sweep: ${err.message}`);
    }

    return false;
  }

  /**
   * Get balance of a specific ledger account
   */
  async getAccountBalance(code: string): Promise<number> {
    if (!this.dbService.db) return 5000; // Simulated fallback
    const account = await this.dbService.db.query.accounts.findFirst({
      where: (fields, { eq }) => eq(fields.code, code),
    });
    return account ? Number(account.balanceCents) / 100 : 0;
  }

  /**
   * Get balance for an isolated user wallet liability account
   */
  async getUserWalletBalance(userId: string): Promise<number> {
    const userWalletCode = await this.getOrCreateUserWallet(userId);
    return await this.getAccountBalance(userWalletCode);
  }
}
