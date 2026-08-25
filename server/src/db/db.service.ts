import { Injectable, OnModuleInit, OnModuleDestroy, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { drizzle, NodePgDatabase } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import * as schema from './schema';

@Injectable()
export class DbService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(DbService.name);
  private pool: Pool;
  public db: NodePgDatabase<typeof schema>;

  constructor(private configService: ConfigService) {}

  async onModuleInit() {
    const databaseUrl = this.configService.get<string>('DATABASE_URL') || 
      'postgresql://gateway_admin:gateway_secure_pass_2026@localhost:5432/rewards_gateway';

    this.logger.log(`Connecting to PostgreSQL Database...`);
    try {
      this.pool = new Pool({
        connectionString: databaseUrl,
        ssl: databaseUrl.includes('sslmode=require') || databaseUrl.includes('railway') ? { rejectUnauthorized: false } : false,
        max: 20,
        idleTimeoutMillis: 30000,
        connectionTimeoutMillis: 5000,
      });

      this.db = drizzle(this.pool, { schema });
      this.logger.log(`Successfully connected to PostgreSQL Database.`);

      await this.seedChartOfAccounts();
    } catch (error) {
      this.logger.error(`Failed to connect to database: ${error.message}. Database operations will run in simulated fallback mode.`);
    }
  }

  async onModuleDestroy() {
    if (this.pool) {
      this.logger.log(`Closing PostgreSQL Connection Pool...`);
      await this.pool.end();
    }
  }

  private async seedChartOfAccounts() {
    try {
      this.logger.log(`Initializing & Seeding Standard Chart of Accounts...`);
      const systemAccounts = [
        { code: 'ASSET:CASH_FLOAT_INR', name: 'Main Operating Cash Float', type: 'ASSET', currency: 'INR', balanceCents: 100000000n }, // ₹1,000,000.00
        { code: 'ASSET:ZENDIT_PREPAID_INR', name: 'Zendit Gateway Prepaid Float', type: 'ASSET', currency: 'INR', balanceCents: 50000000n },   // ₹500,000.00
        { code: 'ASSET:GIFTBIT_PREPAID_INR', name: 'Giftbit Brand Float', type: 'ASSET', currency: 'INR', balanceCents: 25000000n },  // ₹250,000.00
        { code: 'LIABILITY:USER_WALLETS', name: 'Customer Wallet Balances', type: 'LIABILITY', currency: 'INR', balanceCents: 50000000n }, // ₹500,000.00
        { code: 'LIABILITY:PENDING_DISPATCH', name: 'Orders In-Flight Escrow', type: 'LIABILITY', currency: 'INR', balanceCents: 0n },
        { code: 'REVENUE:GATEWAY_FEES', name: 'Marketplace Transaction Margin', type: 'REVENUE', currency: 'INR', balanceCents: 0n },
        { code: 'EXPENSE:GATEWAY_FEES', name: 'Provider Wholesale Cost of Goods', type: 'EXPENSE', currency: 'INR', balanceCents: 0n },
        { code: 'EXPENSE:REFUNDS', name: 'Settled Customer Refunds', type: 'EXPENSE', currency: 'INR', balanceCents: 0n }
      ];

      for (const account of systemAccounts) {
        const existing = await this.db.query.accounts.findFirst({
          where: (fields, { eq }) => eq(fields.code, account.code),
        });

        if (!existing) {
          await this.db.insert(schema.accounts).values({
            code: account.code,
            name: account.name,
            type: account.type,
            currency: account.currency,
            balanceCents: account.balanceCents,
            isActive: true,
          });
          this.logger.log(`Created account code: ${account.code}`);
        }
      }
      this.logger.log(`Seeding complete.`);
    } catch (e) {
      this.logger.error(`Error seeding chart of accounts: ${e.message}`);
    }
  }
}
