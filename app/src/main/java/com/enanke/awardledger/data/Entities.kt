package com.enanke.awardledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enanke.awardledger.domain.model.Category
import com.enanke.awardledger.domain.model.LedgerStatus
import com.enanke.awardledger.domain.model.ProcessState
import com.enanke.awardledger.domain.model.ReviewStatus
import com.enanke.awardledger.domain.model.RosterEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 名单成员。仅用姓名 + 内部 id 标识，绝不使用学号。
 * 姓名不加唯一约束，以便测试与防御“同名”场景（真实名单当前互不重复）。
 * [note] 为同名时的区分信息（如组别），由用户确认后消歧，绝不用学号。
 */
@Entity(tableName = "members", indices = [Index("name")])
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "active") val active: Boolean = true,
) {
    fun toRosterEntry() = RosterEntry(id = id, name = name, note = note)
}

/**
 * 聊天原话：持久化原始文本与发送时间，离线也能显示“待处理”。
 * client_message_id 唯一，用于幂等（重复发送不重复入账）。
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["client_message_id"], unique = true)],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_message_id") val clientMessageId: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "sent_at") val sentAtMillis: Long,
    @ColumnInfo(name = "process_state") val processState: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
) {
    val state: ProcessState get() = runCatching { ProcessState.valueOf(processState) }.getOrDefault(ProcessState.PENDING)
}

/**
 * AI 结构化的候选事迹（待确认）。idempotency_key 唯一，防止重试重复生成。
 * [statedName] 保留 AI 原始姓名用于展示与追问；[memberId] 是唯一匹配/用户选定后的内部成员 id。
 */
@Entity(
    tableName = "candidate_events",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chat_message_id"), Index(value = ["idempotency_key"], unique = true)],
)
data class CandidateEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "chat_message_id") val chatMessageId: Long,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "stated_name") val statedName: String?,
    @ColumnInfo(name = "member_id") val memberId: Long?,
    @ColumnInfo(name = "member_name") val memberName: String?,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "review_status") val reviewStatus: String,
    @ColumnInfo(name = "question") val question: String?,
) {
    val status: ReviewStatus get() = runCatching { ReviewStatus.valueOf(reviewStatus) }.getOrDefault(ReviewStatus.NEEDS_NAME)
    val categoryEnum: Category? get() = category?.let { Category.fromCode(it) }
}

/**
 * 台账记录。更正时保留原记录（SUPERSEDED）并写入新记录（CURRENT），
 * 变更关系通过 supersedes_id / superseded_by_id 表达。idempotency_key 唯一防重复入账。
 * [memberId] 为内部成员 id，与姓名一同写入，保证同名时不张冠李戴。
 */
@Entity(
    tableName = "ledger_records",
    indices = [Index("member_name"), Index(value = ["idempotency_key"], unique = true)],
)
data class LedgerRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "member_id") val memberId: Long,
    @ColumnInfo(name = "member_name") val memberName: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "source_candidate_id") val sourceCandidateId: Long,
    @ColumnInfo(name = "source_message_id") val sourceMessageId: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "supersedes_id") val supersedesId: Long?,
    @ColumnInfo(name = "superseded_by_id") val supersededById: Long?,
    @ColumnInfo(name = "revision") val revision: Int = 1,
) {
    val recordStatus: LedgerStatus get() = runCatching { LedgerStatus.valueOf(status) }.getOrDefault(LedgerStatus.CURRENT)
    val categoryEnum: Category get() = Category.fromCode(category) ?: Category.REWARD
}

/** 台账记录快照，用于审计日志的 before/after JSON（安全转义由 kotlinx.serialization 保证）。 */
@Serializable
data class LedgerSnapshot(
    val id: Long,
    val memberId: Long?,
    val memberName: String,
    val category: String,
    val description: String,
    val revision: Int,
    val status: String,
)

fun LedgerRecordEntity.toSnapshot(): LedgerSnapshot = LedgerSnapshot(
    id = id,
    memberId = memberId,
    memberName = memberName,
    category = category,
    description = description,
    revision = revision,
    status = status,
)

/** 成员快照，用于名册变更（新增/改名/停用/启用）审计的 before/after JSON。 */
@Serializable
data class MemberSnapshot(
    val id: Long,
    val name: String,
    val note: String? = null,
    val active: Boolean,
)

fun MemberEntity.toSnapshot(): MemberSnapshot =
    MemberSnapshot(id = id, name = name, note = note, active = active)

/** 审计 JSON 编解码器（统一安全转义）。 */
object JsonCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodeSnapshot(s: LedgerSnapshot): String = json.encodeToString(LedgerSnapshot.serializer(), s)

    fun decodeSnapshot(text: String): LedgerSnapshot? =
        runCatching { json.decodeFromString(LedgerSnapshot.serializer(), text) }.getOrNull()

    fun encodeMember(s: MemberSnapshot): String = json.encodeToString(MemberSnapshot.serializer(), s)

    fun decodeMember(text: String): MemberSnapshot? =
        runCatching { json.decodeFromString(MemberSnapshot.serializer(), text) }.getOrNull()
}

/**
 * 审计日志：记录每一次入账与更正（含 before/after JSON），与台账写入同事务。
 */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestampMillis: Long,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "before_json") val beforeJson: String?,
    @ColumnInfo(name = "after_json") val afterJson: String?,
)
