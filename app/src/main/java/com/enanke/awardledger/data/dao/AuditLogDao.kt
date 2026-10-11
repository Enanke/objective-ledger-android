package com.enanke.awardledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.enanke.awardledger.data.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC")
    fun allFlow(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC")
    suspend fun all(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log WHERE entity_type = :type AND entity_id = :id ORDER BY timestamp ASC")
    suspend fun forEntity(type: String, id: Long): List<AuditLogEntity>
}
