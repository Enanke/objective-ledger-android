package com.enanke.awardledger.data

import androidx.room.withTransaction
import com.enanke.awardledger.ai.AiGateway
import com.enanke.awardledger.data.dao.AuditLogDao
import com.enanke.awardledger.data.dao.CandidateEventDao
import com.enanke.awardledger.data.dao.ChatMessageDao
import com.enanke.awardledger.data.dao.LedgerRecordDao
import com.enanke.awardledger.data.dao.MemberDao
import com.enanke.awardledger.domain.NameMatcher
import com.enanke.awardledger.domain.QueryNumbering
import com.enanke.awardledger.domain.RosterImport
import com.enanke.awardledger.domain.StructureValidator
import com.enanke.awardledger.domain.model.Category
import com.enanke.awardledger.domain.model.LedgerRecordDto
import com.enanke.awardledger.domain.model.LedgerStatus
import com.enanke.awardledger.domain.model.ProcessState
import com.enanke.awardledger.domain.model.ReviewStatus
import com.enanke.awardledger.domain.model.RosterEntry
import kotlinx.coroutines.flow.Flow

/**
 * 台账仓库：业务唯一入口。所有“写台账 + 写日志”的操作都在同一事务里完成，
 * 保证更正保留原记录、变更关系与审计日志的原子性；所有写操作幂等。
 */
class LedgerRepository(
    private val db: AppDatabase,
    private val gatewayProvider: () -> AiGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val memberDao: MemberDao = db.memberDao()
    private val messageDao: ChatMessageDao = db.chatMessageDao()
    private val candidateDao: CandidateEventDao = db.candidateEventDao()
    private val ledgerDao: LedgerRecordDao = db.ledgerRecordDao()
    private val auditDao: AuditLogDao = db.auditLogDao()

    // ---------- 名单 ----------

    /**
     * 启动初始化：**不写入、也不重置任何名单**。
     *
     * 名册只存在于本机 Room 数据库；新安装为空名册，全部由用户自行录入。
     * 已有数据库的现有成员一律不触碰（保留 active 状态、id 与全部历史数据）。
     * 保留此空实现是为了让调用点稳定，并明确“不种子化、不重置”。
     */
    suspend fun ensureInitialized() {
        // 有意为空。
    }

    /** AI 只关心姓名（用于从原话中抽取），返回纯姓名列表。 */
    suspend fun rosterNames(): List<String> = memberDao.allNames()

    /** 本地核对用完整条目（含内部 id 与区分信息，仅活动成员）。 */
    suspend fun members(): List<RosterEntry> = memberDao.all().map { it.toRosterEntry() }

    /** 可选活动成员（确认 / 更正 / 查询下拉用）。 */
    fun membersFlow(): Flow<List<MemberEntity>> = memberDao.allFlow()

    // ---------- 名册管理（新增 / 批量导入 / 改名 / 停用） ----------

    /** 名册管理界面用：含已停用成员（不参与可选名单）。 */
    fun allMembersFlow(): Flow<List<MemberEntity>> = memberDao.allIncludingInactiveFlow()

    sealed interface RosterResult {
        data class Ok(val member: MemberEntity) : RosterResult
        data class Rejected(val message: String) : RosterResult
    }

    /** 批量导入结果（用于向用户说明新增/跳过/忽略了多少）。 */
    data class RosterImportReport(
        val added: List<String>,
        val skippedExisting: List<String>,
        val ignoredBlank: Int,
    ) {
        val summary: String
            get() = buildString {
                append("新增 ").append(added.size).append(" 人")
                if (skippedExisting.isNotEmpty()) {
                    append("；已存在跳过 ").append(skippedExisting.size).append(" 人")
                }
                if (ignoredBlank > 0) append("；忽略空白项 ").append(ignoredBlank).append(" 个")
            }
    }

    /**
     * 批量导入 / 补充名册（幂等）：逐项去首尾空白、忽略空行。
     * 与**活动**成员同名者跳过，不重复创建；写入同一事务。
     */
    suspend fun importMembers(rawText: String): RosterImportReport {
        val parsed = RosterImport.parseNames(rawText)
        val ignoredBlank = RosterImport.countBlankItems(rawText)
        val added = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        db.withTransaction {
            for (name in parsed) {
                if (memberDao.findByName(name).isNotEmpty()) {
                    skipped.add(name)
                    continue
                }
                val id = memberDao.insert(MemberEntity(name = name))
                if (id > 0) {
                    added.add(name)
                    auditMember("MEMBER_ADD", id, before = null, after = memberDao.getById(id)!!.toSnapshot())
                } else {
                    skipped.add(name)
                }
            }
        }
        return RosterImportReport(added = added, skippedExisting = skipped, ignoredBlank = ignoredBlank)
    }

    /**
     * 单独新增成员。
     *
     * 同名必须**显式消歧**：活动成员中若已存在同名者，必须提供与既有者都不同的“区分信息”，
     * 否则拒绝并说明原因（绝不猜测、绝不静默覆盖）。
     */
    suspend fun addMember(rawName: String, rawNote: String?): RosterResult {
        val name = RosterImport.normalizeName(rawName) ?: return RosterResult.Rejected("姓名不能为空。")
        val note = rawNote?.let { RosterImport.normalizeName(it) }
        return db.withTransaction {
            val conflict = memberDao.findByName(name).firstOrNull { (it.note ?: "") == (note ?: "") }
            if (conflict != null) return@withTransaction RosterResult.Rejected(duplicateMessage(name, note))
            val id = memberDao.insert(MemberEntity(name = name, note = note))
            if (id <= 0) return@withTransaction RosterResult.Rejected("新增失败（数据未变化）。")
            val member = memberDao.getById(id)!!
            auditMember("MEMBER_ADD", id, before = null, after = member.toSnapshot())
            RosterResult.Ok(member)
        }
    }

    /**
     * 改名：**保持 member id 不变**，且**不改动任何台账记录中的历史姓名快照**。
     * 同名规则与 [addMember] 相同（排除自身）。
     */
    suspend fun renameMember(memberId: Long, rawName: String, rawNote: String?): RosterResult {
        val name = RosterImport.normalizeName(rawName) ?: return RosterResult.Rejected("姓名不能为空。")
        val note = rawNote?.let { RosterImport.normalizeName(it) }
        return db.withTransaction {
            val before = memberDao.getById(memberId)
                ?: return@withTransaction RosterResult.Rejected("成员不存在。")
            val conflict = memberDao.findByName(name)
                .filter { it.id != memberId }
                .firstOrNull { (it.note ?: "") == (note ?: "") }
            if (conflict != null) return@withTransaction RosterResult.Rejected(duplicateMessage(name, note))
            memberDao.updateNameAndNote(memberId, name, note)
            val after = memberDao.getById(memberId)!!
            auditMember("MEMBER_RENAME", memberId, before.toSnapshot(), after.toSnapshot())
            RosterResult.Ok(after)
        }
    }

    /**
     * 停用 / 启用。**停用替代删除**：成员行与 id 一律保留，
     * 因此既有台账记录的历史关联与姓名快照不变。写审计日志。
     */
    suspend fun setMemberActive(memberId: Long, active: Boolean): RosterResult = db.withTransaction {
        val before = memberDao.getById(memberId)
            ?: return@withTransaction RosterResult.Rejected("成员不存在。")
        if (before.active == active) return@withTransaction RosterResult.Ok(before)
        memberDao.updateActive(memberId, active)
        val after = memberDao.getById(memberId)!!
        auditMember(
            if (active) "MEMBER_ACTIVATE" else "MEMBER_DEACTIVATE",
            memberId,
            before.toSnapshot(),
            after.toSnapshot(),
        )
        RosterResult.Ok(after)
    }

    private fun duplicateMessage(name: String, note: String?): String =
        if (note == null) {
            "名单中已存在同名成员「$name」。如需再新增或改为同名，必须填写“区分信息”以明确区分（绝不猜测）。"
        } else {
            "名单中已存在同名且区分信息相同的成员（「$name」/「$note」），无法区分；请改用其它区分信息。"
        }

    private suspend fun auditMember(
        action: String,
        memberId: Long,
        before: MemberSnapshot?,
        after: MemberSnapshot?,
    ) {
        auditDao.insert(
            AuditLogEntity(
                timestampMillis = clock(),
                action = action,
                entityType = "MEMBER",
                entityId = memberId,
                beforeJson = before?.let { JsonCodec.encodeMember(it) },
                afterJson = after?.let { JsonCodec.encodeMember(it) },
            ),
        )
    }

    // ---------- 聊天与结构化 ----------

    /** 幂等发送：相同 clientMessageId 直接返回既有记录，绝不重复。 */
    suspend fun sendMessage(clientMessageId: String, rawText: String, sentAt: Long): ChatMessageEntity {
        messageDao.getByClientId(clientMessageId)?.let { return it }
        val msg = ChatMessageEntity(
            clientMessageId = clientMessageId,
            rawText = rawText,
            sentAtMillis = sentAt,
            processState = ProcessState.PENDING.name,
            createdAtMillis = clock(),
        )
        messageDao.insert(msg)
        return messageDao.getByClientId(clientMessageId)!!
    }

    fun messagesFlow(): Flow<List<ChatMessageEntity>> = messageDao.allFlow()
    fun candidatesFlow(): Flow<List<CandidateEventEntity>> = candidateDao.allFlow()
    fun candidatesForReviewFlow(): Flow<List<CandidateEventEntity>> =
        candidateDao.byStatusesFlow(
            listOf(
                ReviewStatus.READY_TO_CONFIRM.name,
                ReviewStatus.NEEDS_NAME.name,
                ReviewStatus.NEEDS_CATEGORY.name,
                ReviewStatus.NEEDS_DESCRIPTION.name,
            ),
        )

    fun pendingMessagesFlow(): Flow<List<ChatMessageEntity>> =
        messageDao.byStatesFlow(listOf(ProcessState.PENDING.name, ProcessState.FAILED.name))

    suspend fun pendingMessages(): List<ChatMessageEntity> =
        messageDao.byStates(listOf(ProcessState.PENDING.name, ProcessState.FAILED.name))

    /**
     * 处理所有待处理/失败消息（联网后重试）。每个消息的处理本身幂等：
     * 候选以 idempotencyKey 去重，重复处理不会产生重复候选。
     */
    suspend fun processPending() {
        val todo = pendingMessages()
        for (m in todo) processMessage(m.id)
    }

    /** 处理单条消息：AI 结构化 -> 本地核对 -> 持久化候选（事务）。 */
    suspend fun processMessage(messageId: Long) {
        val msg = messageDao.getById(messageId) ?: return
        val existing = candidateDao.byMessage(msg.id)
        if (msg.state == ProcessState.DONE && existing.isNotEmpty()) return
        messageDao.updateState(msg.id, ProcessState.PROCESSING.name)
        try {
            val roster = members()
            val raw = gatewayProvider().structure(msg.rawText, roster.map { it.name })
            val validated = StructureValidator.validate(raw, roster)
            if (validated.isEmpty()) {
                messageDao.updateState(msg.id, ProcessState.FAILED.name)
                return
            }
            db.withTransaction {
                for ((i, v) in validated.withIndex()) {
                    candidateDao.insert(
                        CandidateEventEntity(
                            chatMessageId = msg.id,
                            idempotencyKey = "${msg.clientMessageId}#$i",
                            statedName = v.raw.name.takeIf { it.isNotBlank() },
                            memberId = v.resolvedMemberId,
                            memberName = v.resolvedName,
                            category = v.resolvedCategory?.name,
                            description = v.raw.description,
                            reviewStatus = v.status.name,
                            question = v.message,
                        ),
                    )
                }
                messageDao.updateState(msg.id, ProcessState.PARSED.name)
            }
        } catch (e: Exception) {
            messageDao.updateState(msg.id, ProcessState.FAILED.name)
        }
    }

    // ---------- 确认 / 追问 / 丢弃 ----------

    sealed interface ConfirmOutcome {
        data class Confirmed(val record: LedgerRecordEntity) : ConfirmOutcome
        data class AlreadyConfirmed(val record: LedgerRecordEntity) : ConfirmOutcome
        data object NotReady : ConfirmOutcome
        data class Error(val message: String) : ConfirmOutcome
    }

    /** 入账（幂等）：候选 -> 台账 + 审计日志，同一事务。 */
    suspend fun confirmCandidate(candidateId: Long): ConfirmOutcome = db.withTransaction {
        val cand = candidateDao.getById(candidateId) ?: return@withTransaction ConfirmOutcome.Error("候选不存在")

        // 幂等：已入账过则直接返回既有台账记录。
        ledgerDao.getByIdempotencyKey(cand.idempotencyKey)?.let { existing ->
            if (cand.status != ReviewStatus.CONFIRMED) {
                candidateDao.updateStatus(cand.id, ReviewStatus.CONFIRMED.name)
            }
            return@withTransaction ConfirmOutcome.AlreadyConfirmed(existing)
        }

        if (cand.status != ReviewStatus.READY_TO_CONFIRM) {
            return@withTransaction ConfirmOutcome.NotReady
        }

        val memberId = cand.memberId ?: return@withTransaction ConfirmOutcome.Error("成员未确定")
        val name = cand.memberName ?: return@withTransaction ConfirmOutcome.Error("姓名缺失")
        val cat = cand.category ?: return@withTransaction ConfirmOutcome.Error("类别缺失")
        val desc = cand.description ?: return@withTransaction ConfirmOutcome.Error("事迹缺失")

        val record = LedgerRecordEntity(
            idempotencyKey = cand.idempotencyKey,
            memberId = memberId,
            memberName = name,
            category = cat,
            description = desc,
            createdAtMillis = clock(),
            sourceCandidateId = cand.id,
            sourceMessageId = cand.chatMessageId,
            status = LedgerStatus.CURRENT.name,
            supersedesId = null,
            supersededById = null,
            revision = 1,
        )
        val newId = ledgerDao.insert(record)
        candidateDao.updateStatus(cand.id, ReviewStatus.CONFIRMED.name)
        auditDao.insert(
            AuditLogEntity(
                timestampMillis = clock(),
                action = "CONFIRM",
                entityType = "LEDGER_RECORD",
                entityId = newId,
                beforeJson = null,
                afterJson = JsonCodec.encodeSnapshot(record.copy(id = newId).toSnapshot()),
            ),
        )
        maybeFinishMessage(cand.chatMessageId)
        ConfirmOutcome.Confirmed(record.copy(id = newId))
    }

    suspend fun discardCandidate(candidateId: Long) = db.withTransaction {
        val cand = candidateDao.getById(candidateId) ?: return@withTransaction
        candidateDao.updateStatus(cand.id, ReviewStatus.DISCARDED.name)
        maybeFinishMessage(cand.chatMessageId)
    }

    /**
     * 追问应答：用户补充/修正字段后重新核对。
     * 支持通过内部 [memberId] 选定同名成员（消歧，绝不用学号、绝不猜测）。
     */
    suspend fun clarifyCandidate(
        candidateId: Long,
        memberId: Long? = null,
        name: String? = null,
        categoryCode: String? = null,
        description: String? = null,
    ): ReviewStatus {
        val cand = candidateDao.getById(candidateId) ?: return ReviewStatus.NEEDS_NAME
        val members = members()

        val nameResult = when {
            memberId != null -> members.firstOrNull { it.id == memberId }
                ?.let { NameMatcher.MatchResult.unique(it) }
                ?: NameMatcher.MatchResult.notFound()
            name != null -> NameMatcher.match(name, true, members)
            cand.memberId != null -> members.firstOrNull { it.id == cand.memberId }
                ?.let { NameMatcher.MatchResult.unique(it) }
                ?: NameMatcher.MatchResult.notFound()
            else -> NameMatcher.match(cand.statedName ?: "", true, members)
        }

        val finalCategory = Category.fromCode(categoryCode) ?: cand.categoryEnum
        val finalDescription = (description ?: cand.description).orEmpty()

        val status = when {
            nameResult.status != NameMatcher.MatchStatus.UNIQUE -> ReviewStatus.NEEDS_NAME
            finalCategory == null -> ReviewStatus.NEEDS_CATEGORY
            finalDescription.isBlank() -> ReviewStatus.NEEDS_DESCRIPTION
            else -> ReviewStatus.READY_TO_CONFIRM
        }

        candidateDao.updateFields(
            id = cand.id,
            memberId = nameResult.resolvedMemberId,
            name = nameResult.resolvedName,
            category = finalCategory?.name,
            description = finalDescription,
            status = status.name,
        )
        return status
    }

    private suspend fun maybeFinishMessage(messageId: Long) {
        val cands = candidateDao.byMessage(messageId)
        if (cands.isNotEmpty() && cands.all { it.status == ReviewStatus.CONFIRMED || it.status == ReviewStatus.DISCARDED }) {
            messageDao.updateState(messageId, ProcessState.DONE.name)
        }
    }

    // ---------- 更正 ----------

    sealed interface CorrectOutcome {
        data class Corrected(val record: LedgerRecordEntity) : CorrectOutcome
        data class AlreadyCorrected(val current: LedgerRecordEntity) : CorrectOutcome
        data class Error(val message: String) : CorrectOutcome
    }

    /**
     * 更正台账记录（事务）：保留原记录并标记 SUPERSEDED，插入新 CURRENT 记录，
     * 同时写入审计日志。三者原子提交；重复调用返回已更正的当前记录（幂等）。
     */
    suspend fun correctRecord(
        recordId: Long,
        newMemberId: Long,
        newCategory: Category,
        newDescription: String,
    ): CorrectOutcome = db.withTransaction {
        val old = ledgerDao.getById(recordId) ?: return@withTransaction CorrectOutcome.Error("记录不存在")

        // 幂等：若原记录已被更正，返回其当前记录，避免重复更正。
        if (old.status != LedgerStatus.CURRENT.name) {
            val current = old.supersededById?.let { ledgerDao.getById(it) }
            return@withTransaction current?.let { CorrectOutcome.AlreadyCorrected(it) }
                ?: CorrectOutcome.Error("该记录已被更正，但未找到当前记录")
        }

        if (newDescription.isBlank()) {
            return@withTransaction CorrectOutcome.Error("事迹描述不能为空")
        }
        val target = memberDao.getById(newMemberId)
            ?: return@withTransaction CorrectOutcome.Error("目标成员不存在")

        val newRecord = old.copy(
            id = 0,
            idempotencyKey = "${old.idempotencyKey}#corr${old.revision + 1}",
            memberId = target.id,
            memberName = target.name,
            category = newCategory.name,
            description = newDescription,
            createdAtMillis = clock(),
            status = LedgerStatus.CURRENT.name,
            supersedesId = old.id,
            supersededById = null,
            revision = old.revision + 1,
        )
        val newId = ledgerDao.insert(newRecord)
        ledgerDao.markSuperseded(old.id, newId)
        auditDao.insert(
            AuditLogEntity(
                timestampMillis = clock(),
                action = "CORRECT",
                entityType = "LEDGER_RECORD",
                entityId = newId,
                beforeJson = JsonCodec.encodeSnapshot(old.toSnapshot()),
                afterJson = JsonCodec.encodeSnapshot(newRecord.copy(id = newId).toSnapshot()),
            ),
        )
        CorrectOutcome.Corrected(newRecord.copy(id = newId))
    }

    // ---------- 查询 ----------

    fun currentRecordsFlow(): Flow<List<LedgerRecordEntity>> = ledgerDao.currentFlow()

    /** 按姓名查成员（可能多人同名），供查询界面先消歧。 */
    suspend fun findMembersByName(name: String): List<MemberEntity> = memberDao.findByName(name)

    /** 按内部成员 id 读取台账（同名成员互不混合）。 */
    suspend fun ledgerForMember(memberId: Long): List<LedgerRecordEntity> =
        ledgerDao.currentByMemberId(memberId)

    /**
     * 查询结果：完全由数据库确定性生成（含真实记录 ID 与姓名），不经过 AI，
     * 从根本上保证逐条对应真实记录、无法编造或遗漏。
     */
    suspend fun summarizeRecords(memberId: Long): String {
        val records = ledgerDao.currentByMemberId(memberId)
        if (records.isEmpty()) return "该成员暂无台账记录。"
        return QueryNumbering.buildDeterministic(records.map { it.toDto() })
    }

    // ---------- 恢复与核查 ----------

    /** 恢复中断写入：重置遗留 PROCESSING，便于重试。 */
    suspend fun recoverInterruptedWrites() {
        messageDao.resetStaleProcessing()
    }

    /** 一致性核查：返回异常说明列表（空表示一致）。 */
    suspend fun verifyConsistency(): List<String> {
        val issues = mutableListOf<String>()
        val allCandidates = candidateDao.all()
        for (c in allCandidates.filter { it.status == ReviewStatus.CONFIRMED }) {
            if (ledgerDao.getByIdempotencyKey(c.idempotencyKey) == null) {
                issues.add("候选 #${c.id} 已确认但缺少对应台账记录")
            }
        }
        val currentRecords = ledgerDao.current()
        for (r in currentRecords) {
            val logs = auditDao.forEntity("LEDGER_RECORD", r.id)
            if (logs.isEmpty()) issues.add("台账记录 #${r.id} 缺少审计日志")
        }
        val processing = messageDao.byStates(listOf(ProcessState.PROCESSING.name))
        for (m in processing) issues.add("消息 #${m.id} 仍处于 PROCESSING（可能中断）")
        return issues
    }

    fun auditLogFlow(): Flow<List<AuditLogEntity>> = auditDao.allFlow()

    private fun LedgerRecordEntity.toDto() = LedgerRecordDto(
        id = id,
        name = memberName,
        category = category,
        description = description,
    )
}
