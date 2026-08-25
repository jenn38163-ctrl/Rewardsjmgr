package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AccountType
import com.example.model.EntryDirection

@Entity(tableName = "local_accounts")
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val type: String, // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    val currency: String = "INR",
    val balanceCents: Long = 0L
)

@Entity(tableName = "local_journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val correlationId: String,
    val referenceType: String,
    val referenceId: String,
    val description: String,
    val postedAt: Long
)

@Entity(tableName = "local_ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val journalId: String,
    val accountCode: String,
    val amountCents: Long,
    val direction: String, // DEBIT, CREDIT
    val currency: String = "INR",
    val createdAt: Long
)
