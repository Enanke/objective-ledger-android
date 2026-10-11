package com.enanke.awardledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enanke.awardledger.data.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE client_message_id = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: Long): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages ORDER BY sent_at ASC, id ASC")
    fun allFlow(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE process_state IN (:states) ORDER BY sent_at ASC, id ASC")
    fun byStatesFlow(states: List<String>): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE process_state IN (:states) ORDER BY sent_at ASC, id ASC")
    suspend fun byStates(states: List<String>): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET process_state = :newState WHERE id = :id")
    suspend fun updateState(id: Long, newState: String)

    /** 恢复：把中断遗留的 PROCESSING 消息重置为 PENDING，便于重试。 */
    @Query("UPDATE chat_messages SET process_state = 'PENDING' WHERE process_state = 'PROCESSING'")
    suspend fun resetStaleProcessing()
}
