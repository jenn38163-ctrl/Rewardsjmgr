import { pgTable, uuid, varchar, bigint, boolean, timestamp, jsonb, integer, text, pgEnum } from 'drizzle-orm/pg-core';

export const accountTypeEnum = pgEnum('account_type', ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']);
export const entryDirectionEnum = pgEnum('entry_direction', ['DEBIT', 'CREDIT']);
export const orderStateEnum = pgEnum('order_state', ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'REFUNDED']);

// 1. Chart of Accounts Table
export const accounts = pgTable('accounts', {
  id: uuid('id').primaryKey().defaultRandom(),
  code: varchar('code', { length: 64 }).unique().notNull(), // e.g., 'LIABILITY:USER_WALLETS'
  name: varchar('name', { length: 128 }).notNull(),
  type: varchar('type', { length: 32 }).notNull(), // 'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'
  currency: varchar('currency', { length: 3 }).default('INR').notNull(),
  balanceCents: bigint('balance_cents', { mode: 'bigint' }).default(0n).notNull(),
  isActive: boolean('is_active').default(true).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).defaultNow().notNull()
});

// 2. Journal Entries (Atomic Transaction Header)
export const journalEntries = pgTable('journal_entries', {
  id: uuid('id').primaryKey().defaultRandom(),
  correlationId: varchar('correlation_id', { length: 64 }).unique().notNull(),
  referenceType: varchar('reference_type', { length: 64 }).notNull(), // 'PURCHASE_ORDER', 'WALLET_TOPUP', etc.
  referenceId: varchar('reference_id', { length: 64 }).notNull(),
  description: text('description').notNull(),
  metadata: jsonb('metadata').default({}).notNull(),
  postedAt: timestamp('posted_at', { withTimezone: true }).defaultNow().notNull()
});

// 3. Ledger Line Items (Debits & Credits)
export const ledgerEntries = pgTable('ledger_entries', {
  id: uuid('id').primaryKey().defaultRandom(),
  journalId: uuid('journal_id').references(() => journalEntries.id, { onDelete: 'cascade' }).notNull(),
  accountId: uuid('account_id').references(() => accounts.id).notNull(),
  amountCents: bigint('amount_cents', { mode: 'bigint' }).notNull(),
  direction: varchar('direction', { length: 6 }).notNull(), // 'DEBIT' or 'CREDIT'
  currency: varchar('currency', { length: 3 }).default('INR').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull()
});

// 4. Gateway Orders with Pessimistic Locking Support
export const gatewayOrders = pgTable('gateway_orders', {
  id: uuid('id').primaryKey().defaultRandom(),
  orderNumber: varchar('order_number', { length: 64 }).unique().notNull(),
  userId: varchar('user_id', { length: 64 }).notNull(),
  idempotencyKey: varchar('idempotency_key', { length: 128 }).unique().notNull(),
  provider: varchar('provider', { length: 32 }).notNull(), // 'ZENDIT', 'GIFTBIT', 'INTERNAL'
  itemType: varchar('item_type', { length: 32 }).notNull(), // 'GIFT_CARD', 'ESIM_PLAN', 'MOBILE_TOPUP'
  title: varchar('title', { length: 255 }).notNull(),
  amountInr: integer('amount_inr').notNull(),
  state: varchar('state', { length: 32 }).default('PENDING').notNull(), // 'PENDING', 'PROCESSING', etc.
  recipientMasked: varchar('recipient_masked', { length: 128 }).notNull(),
  voucherCodeEncrypted: text('voucher_code_encrypted'),
  voucherPinEncrypted: text('voucher_pin_encrypted'),
  retryCount: integer('retry_count').default(0).notNull(),
  maxRetries: integer('max_retries').default(3).notNull(),
  failureReason: text('failure_reason'),
  lockVersion: integer('lock_version').default(0).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).defaultNow().notNull()
});

// 5. Distributed Idempotency Locks table
export const idempotencyLocks = pgTable('idempotency_locks', {
  key: varchar('key', { length: 128 }).primaryKey(),
  userId: varchar('user_id', { length: 64 }).notNull(),
  lockedUntil: timestamp('locked_until', { withTimezone: true }).notNull(),
  responsePayload: jsonb('response_payload'),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull()
});

// 6. Auto-Replenishment Threshold Rules
export const autoReplenishRules = pgTable('auto_replenish_rules', {
  id: uuid('id').primaryKey().defaultRandom(),
  accountId: uuid('account_id').references(() => accounts.id).notNull(),
  provider: varchar('provider', { length: 32 }).notNull(),
  thresholdCents: bigint('threshold_cents', { mode: 'bigint' }).notNull(),
  replenishAmountCents: bigint('replenish_amount_cents', { mode: 'bigint' }).notNull(),
  isEnabled: boolean('is_enabled').default(true).notNull(),
  lastTriggeredAt: timestamp('last_triggered_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull()
});

// 7. Processed Webhooks Idempotency Log
export const processedWebhooks = pgTable('processed_webhooks', {
  eventId: varchar('event_id', { length: 128 }).primaryKey(),
  orderId: varchar('order_id', { length: 64 }).notNull(),
  processedAt: timestamp('processed_at', { withTimezone: true }).defaultNow().notNull()
});

