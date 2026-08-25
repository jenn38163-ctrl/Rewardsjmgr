package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Query("SELECT * FROM local_accounts")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM local_accounts WHERE code = :code")
    suspend fun getAccountByCode(code: String): AccountEntity?

    @Query("UPDATE local_accounts SET balanceCents = balanceCents + :deltaCents WHERE code = :code")
    suspend fun updateAccountBalance(code: String, deltaCents: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournal(journal: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntries(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM local_journal_entries ORDER BY postedAt DESC")
    fun getAllJournals(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM local_ledger_entries WHERE journalId = :journalId")
    suspend fun getEntriesForJournal(journalId: String): List<LedgerEntryEntity>

    @Transaction
    suspend fun postDoubleEntryTransaction(
        journal: JournalEntryEntity,
        entries: List<LedgerEntryEntity>
    ) {
        // Enforce balanced entry
        val debits = entries.filter { it.direction == "DEBIT" }.sumOf { it.amountCents }
        val credits = entries.filter { it.direction == "CREDIT" }.sumOf { it.amountCents }
        if (debits != credits) {
            throw IllegalArgumentException("Double entry unbalanced! Debits=$debits, Credits=$credits")
        }

        insertJournal(journal)
        insertLedgerEntries(entries)

        // Adjust balances based on account types
        entries.forEach { entry ->
            val delta = if (entry.direction == "CREDIT") entry.amountCents else -entry.amountCents
            updateAccountBalance(entry.accountCode, delta)
        }
    }
}
