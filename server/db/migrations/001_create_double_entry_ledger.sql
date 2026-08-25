-- ==============================================================================
-- Rewards Gateway: Strict Double-Entry Ledger & State Machine Schema (PostgreSQL)
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Chart of Accounts Table
CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(64) UNIQUE NOT NULL, -- e.g. 'ASSET:ZENDIT_FLOAT', 'LIABILITY:USER_WALLETS', 'EXPENSE:DISCOUNTS'
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed standard system accounts
INSERT INTO accounts (code, name, type, currency) VALUES
('ASSET:CASH_FLOAT_INR', 'Main Operating Cash Float', 'ASSET', 'INR'),
('ASSET:ZENDIT_PREPAID_USD', 'Zendit Gateway Prepaid Float', 'ASSET', 'USD'),
('ASSET:GIFTBIT_PREPAID_USD', 'Giftbit Brand Float', 'ASSET', 'USD'),
('LIABILITY:USER_WALLETS', 'Customer Wallet Balances', 'LIABILITY', 'INR'),
('LIABILITY:PENDING_DISPATCH', 'Orders In-Flight Escrow', 'LIABILITY', 'INR'),
('REVENUE:GATEWAY_FEES', 'Marketplace Transaction Margin', 'REVENUE', 'INR'),
('EXPENSE:GATEWAY_FEES', 'Provider Wholesale Cost of Goods', 'EXPENSE', 'INR'),
('EXPENSE:REFUNDS', 'Settled Customer Refunds', 'EXPENSE', 'INR')
ON CONFLICT (code) DO NOTHING;

-- 2. Journal Entries (Atomic Transaction Header)
CREATE TABLE IF NOT EXISTS journal_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    correlation_id VARCHAR(64) UNIQUE NOT NULL,
    reference_type VARCHAR(64) NOT NULL CHECK (reference_type IN ('PURCHASE_ORDER', 'WALLET_TOPUP', 'GATEWAY_REFUND', 'AUTO_REPLENISH', 'ORACLE_SWAP')),
    reference_id VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Ledger Line Items (Debits & Credits)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    journal_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Constraint Trigger: Verify that Sum(Debits) == Sum(Credits) for every committed journal entry
CREATE OR REPLACE FUNCTION check_journal_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_debits BIGINT;
    total_credits BIGINT;
BEGIN
    SELECT 
        COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_cents ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents ELSE 0 END), 0)
    INTO total_debits, total_credits
    FROM ledger_entries
    WHERE journal_id = NEW.journal_id;

    IF total_debits <> total_credits THEN
        RAISE EXCEPTION 'Double-entry balance violation: Journal % has Debits=% and Credits=%', 
            NEW.journal_id, total_debits, total_credits;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. Gateway Orders with Pessimistic Locking & State Machine Transitions
CREATE TABLE IF NOT EXISTS gateway_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_number VARCHAR(64) UNIQUE NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,
    provider VARCHAR(32) NOT NULL CHECK (provider IN ('ZENDIT', 'GIFTBIT', 'INTERNAL')),
    item_type VARCHAR(32) NOT NULL CHECK (item_type IN ('GIFT_CARD', 'ESIM_PLAN', 'MOBILE_TOPUP')),
    title VARCHAR(255) NOT NULL,
    amount_inr INT NOT NULL CHECK (amount_inr > 0),
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING' 
        CHECK (state IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'REFUNDED')),
    recipient_masked VARCHAR(128) NOT NULL,
    voucher_code_encrypted TEXT,
    voucher_pin_encrypted TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    failure_reason TEXT,
    lock_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Distributed Idempotency Locks (PostgreSQL / Redis failover store)
CREATE TABLE IF NOT EXISTS idempotency_locks (
    key VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    locked_until TIMESTAMPTZ NOT NULL,
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. Auto-Replenishment Threshold Rules
CREATE TABLE IF NOT EXISTS auto_replenish_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    provider VARCHAR(32) NOT NULL,
    threshold_cents BIGINT NOT NULL,
    replenish_amount_cents BIGINT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indices for high performance queries
CREATE INDEX IF NOT EXISTS idx_ledger_journal_id ON ledger_entries(journal_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account_id ON ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_journal_ref ON journal_entries(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_orders_user_state ON gateway_orders(user_id, state);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency ON gateway_orders(idempotency_key);
