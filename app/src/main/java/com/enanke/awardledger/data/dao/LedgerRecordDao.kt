package com.enanke.awardledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enanke.awardledger.data.LedgerRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: LedgerRecordEntity): Long

    @Query("SELECT * FROM ledger_records WHERE id = :id")
    suspend fun getById(id: Long): LedgerRecordEntity?

    @Query("SELECT * FROM ledger_records WHERE idempotency_key = :key LIMIT 1")
    suspend fun getByIdempotencyKey(key: String): LedgerRecordEntity?

    @Query("SELECT * FROM ledger_records WHERE status = 'CURRENT' AND member_id = :memberId ORDER BY id DESC")
    suspend fun currentByMemberId(memberId: Long): List<LedgerRecordEntity>

    @Query("SELECT * FROM ledger_records WHERE status = 'CURRENT' ORDER BY id DESC")
    fun currentFlow(): Flow<List<LedgerRecordEntity>>

    @Query("SELECT * FROM ledger_records WHERE status = 'CURRENT' ORDER BY id DESC")
    suspend fun current(): List<LedgerRecordEntity>

    @Query("SELECT * FROM ledger_records ORDER BY id DESC")
    suspend fun all(): List<LedgerRecordEntity>

    @Query("UPDATE ledger_records SET status = 'SUPERSEDED', superseded_by_id = :newId WHERE id = :id")
    suspend fun markSuperseded(id: Long, newId: Long)
}
