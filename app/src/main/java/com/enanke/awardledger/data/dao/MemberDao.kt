package com.enanke.awardledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enanke.awardledger.data.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(members: List<MemberEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(member: MemberEntity): Long

    /** 可选活动成员姓名（仅 active=1），供姓名核对与消歧。 */
    @Query("SELECT name FROM members WHERE active = 1 ORDER BY id")
    suspend fun allNames(): List<String>

    @Query("SELECT * FROM members WHERE active = 1 ORDER BY id")
    suspend fun all(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE active = 1 ORDER BY id")
    fun allFlow(): Flow<List<MemberEntity>>

    /** 仅活动成员：同名冲突判定只看“可选名单”。 */
    @Query("SELECT * FROM members WHERE active = 1 AND name = :name ORDER BY id")
    suspend fun findByName(name: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE name = :name ORDER BY id")
    suspend fun findByNameAnyState(name: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getById(id: Long): MemberEntity?

    /** 名册管理用：含已停用成员（不参与可选名单），启用在前。 */
    @Query("SELECT * FROM members ORDER BY active DESC, id ASC")
    fun allIncludingInactiveFlow(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY id")
    suspend fun allIncludingInactive(): List<MemberEntity>

    @Query("UPDATE members SET name = :name, note = :note WHERE id = :id")
    suspend fun updateNameAndNote(id: Long, name: String, note: String?)

    @Query("UPDATE members SET active = :active WHERE id = :id")
    suspend fun updateActive(id: Long, active: Boolean)

    @Query("SELECT COUNT(*) FROM members WHERE active = 1")
    suspend fun activeCount(): Int
}
