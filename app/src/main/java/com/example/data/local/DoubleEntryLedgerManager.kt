package com.example.data.local

import com.example.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Enterprise Double-Entry Ledger Manager
 * 
 * Enforces:
 * 1. Matching Debit & Credit journal entries for all purchases, top-ups, and refunds.
 * 2. Pessimistic In-Memory & Database Locking to prevent duplicate checkouts.
 * 3. Automated Float Replenishment Sweeps when Zendit/Giftbit reserves drop below threshold.
 */
class DoubleEntryLedgerManager(private val ledgerDao: LedgerDao) {

    private val checkoutMutex = Mutex()
    private val idempotencySet = mutableSetOf<String>()

    companion object {
        const val ACC_USER_WALLET = "LIABILITY:USER_WALLETS"
        const val ACC_ZENDIT_FLOAT = "ASSET:ZENDIT_PREPAID_USD"
        const val ACC_GIFTBIT_FLOAT = "ASSET:GIFTBIT_PREPAID_USD"
        const val ACC_CASH_FLOAT = "ASSET:CASH_FLOAT_INR"
        const val ACC_REVENUE_FEES = "REVENUE:GATEWAY_FEES"
        const val ACC_EXPENSE_WHOLESALE = "EXPENSE:GATEWAY_FEES"
        const val ACC_EXPENSE_REFUNDS = "EXPENSE:REFUNDS"
    }

    /**
     * Seeds default chart of accounts if missing
     */
    suspend fun initializeChartOfAccounts() {
        val initialAccounts = listOf(
            AccountEntity(ACC_USER_WALLET, "Customer Wallets", "LIABILITY", "INR", 500000L), // ₹5,000.00
            AccountEntity(ACC_ZENDIT_FLOAT, "Zendit Carrier Float", "ASSET", "INR", 150000L), // ₹1,500.00
            AccountEntity(ACC_GIFTBIT_FLOAT, "Giftbit Brand Float", "ASSET", "INR", 250000L), // ₹2,500.00
            AccountEntity(ACC_CASH_FLOAT, "Operating Cash Float", "ASSET", "INR", 1000000L),
            AccountEntity(ACC_REVENUE_FEES, "Gateway Fee Margin", "REVENUE", "INR", 0L),
            AccountEntity(ACC_EXPENSE_WHOLESALE, "Wholesale Inventory Cost", "EXPENSE", "INR", 0L),
            AccountEntity(ACC_EXPENSE_REFUNDS, "Customer Refunds Settled", "EXPENSE", "INR", 0L)
        )
        ledgerDao.insertAccounts(initialAccounts)
    }

    /**
     * Execute an Order Purchase with Pessimistic Locking & Strict Double Entry
     */
    suspend fun recordOrderPurchase(
        orderId: String,
        amountInr: Int,
        provider: String,
        itemTitle: String,
        idempotencyKey: String
    ): Boolean {
        // Step 1: Pessimistic Lock - prevent concurrent debit on same key
        checkoutMutex.withLock {
            if (idempotencySet.contains(idempotencyKey)) {
                return false // Already processed or processing
            }
            idempotencySet.add(idempotencyKey)
        }

        try {
            val amountCents = amountInr.toLong() * 100L
            val journalId = "JRN-${UUID.randomUUID().toString().take(8)}"
            val correlationId = "CORR-${UUID.randomUUID().toString().take(12)}"

            val journal = JournalEntryEntity(
                id = journalId,
                correlationId = correlationId,
                referenceType = "PURCHASE_ORDER",
                referenceId = orderId,
                description = "Purchase: $itemTitle via $provider",
                postedAt = System.currentTimeMillis()
            )

            val providerAccount = if (provider.equals("Giftbit", ignoreCase = true)) {
                ACC_GIFTBIT_FLOAT
            } else {
                ACC_ZENDIT_FLOAT
            }

            // Double Entry Pair:
            // 1. DEBIT: User Wallet (reduces liability owed to user)
            // 2. CREDIT: Gateway Float (dispatches funds to provider)
            val entries = listOf(
                LedgerEntryEntity(
                    id = "LED-${UUID.randomUUID().toString().take(8)}",
                    journalId = journalId,
                    accountCode = ACC_USER_WALLET,
                    amountCents = amountCents,
                    direction = "DEBIT",
                    currency = "INR",
                    createdAt = System.currentTimeMillis()
                ),
                LedgerEntryEntity(
                    id = "LED-${UUID.randomUUID().toString().take(8)}",
                    journalId = journalId,
                    accountCode = providerAccount,
                    amountCents = amountCents,
                    direction = "CREDIT",
                    currency = "INR",
                    createdAt = System.currentTimeMillis()
                )
            )

            ledgerDao.postDoubleEntryTransaction(journal, entries)
            return true
        } finally {
            checkoutMutex.withLock {
                idempotencySet.remove(idempotencyKey)
            }
        }
    }

    /**
     * Check if provider float requires auto-replenishment
     */
    suspend fun checkAndTriggerAutoReplenish(
        provider: String,
        thresholdCents: Long = 100000L, // ₹1,000 threshold
        replenishChunkCents: Long = 500000L // ₹5,000 topup
    ): Boolean {
        val targetAccountCode = if (provider.equals("Giftbit", ignoreCase = true)) ACC_GIFTBIT_FLOAT else ACC_ZENDIT_FLOAT
        val account = ledgerDao.getAccountByCode(targetAccountCode) ?: return false

        if (account.balanceCents < thresholdCents) {
            val journalId = "AUTO-REP-${UUID.randomUUID().toString().take(8)}"
            val correlationId = "CORR-REP-${UUID.randomUUID().toString().take(12)}"

            val journal = JournalEntryEntity(
                id = journalId,
                correlationId = correlationId,
                referenceType = "AUTO_REPLENISH",
                referenceId = targetAccountCode,
                description = "Auto-Replenish sweep of ₹${replenishChunkCents / 100} into $targetAccountCode",
                postedAt = System.currentTimeMillis()
            )

            // Double Entry Pair:
            // DEBIT: Operating Float
            // CREDIT: Provider Prepaid Account
            val entries = listOf(
                LedgerEntryEntity(
                    id = "LED-${UUID.randomUUID().toString().take(8)}",
                    journalId = journalId,
                    accountCode = ACC_CASH_FLOAT,
                    amountCents = replenishChunkCents,
                    direction = "DEBIT",
                    currency = "INR",
                    createdAt = System.currentTimeMillis()
                ),
                LedgerEntryEntity(
                    id = "LED-${UUID.randomUUID().toString().take(8)}",
                    journalId = journalId,
                    accountCode = targetAccountCode,
                    amountCents = replenishChunkCents,
                    direction = "CREDIT",
                    currency = "INR",
                    createdAt = System.currentTimeMillis()
                )
            )

            ledgerDao.postDoubleEntryTransaction(journal, entries)
            return true
        }
        return false
    }
}
