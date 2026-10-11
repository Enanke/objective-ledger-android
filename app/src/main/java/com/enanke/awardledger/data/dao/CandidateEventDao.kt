package com.enanke.awardledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enanke.awardledger.data.CandidateEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CandidateEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(candidate: CandidateEventEntity): Long

    @Query("SELECT * FROM candidate_events WHERE chat_message_id = :messageId ORDER BY id ASC")
    suspend fun byMessage(messageId: Long): List<CandidateEventEntity>

    @Query("SELECT * FROM candidate_events WHERE id = :id")
    suspend fun getById(id: Long): CandidateEventEntity?

    @Query("SELECT * FROM candidate_events WHERE idempotency_key = :key LIMIT 1")
    suspend fun getByIdempotencyKey(key: String): CandidateEventEntity?

    @Query("SELECT * FROM candidate_events WHERE review_status IN (:statuses) ORDER BY id ASC")
    fun byStatusesFlow(statuses: List<String>): Flow<List<CandidateEventEntity>>

    @Query("SELECT * FROM candidate_events ORDER BY id ASC")
    fun allFlow(): Flow<List<CandidateEventEntity>>

    @Query("SELECT * FROM candidate_events ORDER BY id ASC")
    suspend fun all(): List<CandidateEventEntity>

    @Query("UPDATE candidate_events SET review_status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query(
        """
        UPDATE candidate_events
        SET member_id = :memberId, member_name = :name, category = :category,
            description = :description, review_status = :status
        WHERE id = :id
        """,
    )
    suspend fun updateFields(
        id: Long,
        memberId: Long?,
        name: String?,
        category: String?,
        description: String?,
        status: String,
    )
}
