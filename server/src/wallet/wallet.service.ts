import { Injectable, Logger } from '@nestjs/common';
import { LedgerService } from '../ledger/ledger.service';
import { DbService } from '../db/db.service';
import * as schema from '../db/schema';

@Injectable()
export class WalletService {
  private readonly logger = new Logger(WalletService.name);

  constructor(
    private readonly ledgerService: LedgerService,
    private readonly dbService: DbService
  ) {}

  async getBalance(userId: string): Promise<{ balanceInr: number }> {
    const balance = await this.ledgerService.getUserWalletBalance(userId);
    return { balanceInr: balance };
  }

  async topUp(userId: string, amountInr: number): Promise<any> {
    this.logger.log(`Processing topup of ₹${amountInr} for User=${userId}`);
    const journalId = await this.ledgerService.recordWalletTopup(userId, amountInr);
    const newBalance = await this.getBalance(userId);
    return {
      success: true,
      journalId,
      newBalance: newBalance.balanceInr,
    };
  }

  async getAuditTrail(): Promise<any[]> {
    if (!this.dbService.db) return [];
    
    // Fetch all journal records with corresponding line item entries
    const journals = await this.dbService.db.query.journalEntries.findMany({
      orderBy: (fields, { desc }) => [desc(fields.postedAt)],
    });

    const enrichedJournals = await Promise.all(
      journals.map(async (j) => {
        const lines = await this.dbService.db.query.ledgerEntries.findMany({
          where: (fields, { eq }) => eq(fields.journalId, j.id),
        });

        // Resolve account codes for readability in audit
        const enrichedLines = await Promise.all(
          lines.map(async (line) => {
            const acc = await this.dbService.db.query.accounts.findFirst({
              where: (fields, { eq }) => eq(fields.id, line.accountId),
            });
            return {
              id: line.id,
              accountCode: acc ? acc.code : 'UNKNOWN',
              accountName: acc ? acc.name : 'Unknown Account',
              amountCents: Number(line.amountCents),
              direction: line.direction,
              currency: line.currency,
            };
          })
        );

        return {
          id: j.id,
          correlationId: j.correlationId,
          referenceType: j.referenceType,
          referenceId: j.referenceId,
          description: j.description,
          postedAt: j.postedAt,
          entries: enrichedLines,
        };
      })
    );

    return enrichedJournals;
  }

  async getChartOfAccounts(): Promise<any[]> {
    if (!this.dbService.db) return [];
    const accounts = await this.dbService.db.query.accounts.findMany();
    return accounts.map(a => ({
      code: a.code,
      name: a.name,
      type: a.type,
      currency: a.currency,
      balanceInr: Number(a.balanceCents) / 100,
    }));
  }
}
