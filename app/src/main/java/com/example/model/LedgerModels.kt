package com.example.model

import java.util.UUID

enum class AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}

enum class EntryDirection {
    DEBIT,
    CREDIT
}

data class ChartOfAccount(
    val id: String = UUID.randomUUID().toString(),
    val code: String,
    val name: String,
    val type: AccountType,
    val currency: String = "INR",
    val balanceCents: Long = 0L
)

data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val correlationId: String = UUID.randomUUID().toString(),
    val referenceType: String, // "PURCHASE_ORDER", "WALLET_TOPUP", "GATEWAY_REFUND", "AUTO_REPLENISH"
    val referenceId: String,
    val description: String,
    val postedAt: Long = System.currentTimeMillis(),
    val entries: List<LedgerEntry> = emptyList()
) {
    /**
     * Strict double-entry verification:
     * Total Debits MUST exactly equal Total Credits.
     */
    val isBalanced: Boolean
        get() {
            val totalDebits = entries.filter { it.direction == EntryDirection.DEBIT }.sumOf { it.amountCents }
            val totalCredits = entries.filter { it.direction == EntryDirection.CREDIT }.sumOf { it.amountCents }
            return totalDebits > 0 && totalDebits == totalCredits
        }
}

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val journalId: String,
    val accountCode: String,
    val amountCents: Long,
    val direction: EntryDirection,
    val currency: String = "INR",
    val createdAt: Long = System.currentTimeMillis()
)

data class AutoReplenishConfig(
    val provider: String,
    val thresholdCents: Long,
    val replenishChunkCents: Long,
    val isEnabled: Boolean = true,
    val lastTriggeredAt: Long? = null
)
