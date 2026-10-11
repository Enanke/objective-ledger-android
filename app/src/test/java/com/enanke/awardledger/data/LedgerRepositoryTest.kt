package com.enanke.awardledger.data

import com.enanke.awardledger.ai.AiGateway
import com.enanke.awardledger.domain.NameMatcher
import com.enanke.awardledger.domain.model.Category
import com.enanke.awardledger.domain.model.LedgerStatus
import com.enanke.awardledger.domain.model.ProcessState
import com.enanke.awardledger.domain.model.RawCandidate
import com.enanke.awardledger.domain.model.ReviewStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 可编程假网关，用于注入确定性的 AI 结构化结果。 */
class FakeAiGateway : AiGateway {
    var structureResult: (String, List<String>) -> List<RawCandidate> = { _, _ -> emptyList() }
    override suspend fun structure(rawText: String, roster: List<String>): List<RawCandidate> =
        structureResult(rawText, roster)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LedgerRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var gateway: FakeAiGateway
    private lateinit var repo: LedgerRepository
    private var now = 1_735_800_000_000L

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = AppDatabase.buildInMemory(context)
        gateway = FakeAiGateway()
        repo = LedgerRepository(db, { gateway }) { now++ }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun raw(
        name: String,
        explicit: Boolean = true,
        category: String = "REWARD",
        description: String = "帮助同学",
        question: String = "",
    ) = RawCandidate(name, explicit, category, description, question)

    private fun seed(vararg names: String) = runBlocking {
        db.memberDao().insertAll(names.map { MemberEntity(name = it) })
    }

    // ---------- 名册管理 ----------

    @Test
    fun `new database has an empty roster and init never seeds`() = runBlocking {
        assertEquals(0, db.memberDao().allIncludingInactive().size)
        repo.ensureInitialized()
        assertEquals(0, db.memberDao().allIncludingInactive().size)
        assertEquals(0, db.memberDao().allNames().size)
    }

    @Test
    fun `init never touches existing members`() = runBlocking {
        db.memberDao().insertAll(
            listOf(MemberEntity(name = "成员甲"), MemberEntity(name = "成员乙", active = false)),
        )
        repo.ensureInitialized()
        // 既有成员（含已停用）保持原样：不新增、不重置、不删除
        assertEquals(2, db.memberDao().allIncludingInactive().size)
        assertEquals(listOf("成员甲"), db.memberDao().allNames())
    }

    @Test
    fun `batch import trims ignores blanks and is idempotent`() = runBlocking {
        val raw = "  张三  \n\n李四\n王五\r\n张三\n"
        val first = repo.importMembers(raw)
        assertEquals(listOf("张三", "李四", "王五"), first.added)
        assertEquals(3, first.ignoredBlank)
        assertEquals(3, db.memberDao().allNames().size)
        assertEquals(3, db.auditLogDao().all().count { it.action == "MEMBER_ADD" })

        // 重复导入幂等：不再新增
        val second = repo.importMembers(raw)
        assertEquals(emptyList<String>(), second.added)
        assertEquals(listOf("张三", "李四", "王五"), second.skippedExisting)
        assertEquals(3, db.memberDao().allNames().size)
    }

    @Test
    fun `blank name is rejected`() = runBlocking {
        assertTrue(repo.addMember("   ", null) is LedgerRepository.RosterResult.Rejected)
        assertTrue(repo.addMember("　", null) is LedgerRepository.RosterResult.Rejected)
        assertEquals(0, db.memberDao().allIncludingInactive().size)
    }

    @Test
    fun `duplicate name must be disambiguated explicitly never guessed`() = runBlocking {
        assertTrue(repo.addMember("张三", null) is LedgerRepository.RosterResult.Ok)

        // 无区分信息 → 明确阻止并说明
        val rejected = repo.addMember("张三", null)
        assertTrue(rejected is LedgerRepository.RosterResult.Rejected)
        assertTrue((rejected as LedgerRepository.RosterResult.Rejected).message.contains("区分信息"))
        assertEquals(1, db.memberDao().allIncludingInactive().size)

        // 区分信息与既有同名者相同 → 仍拒绝
        db.memberDao().insertAll(listOf(MemberEntity(name = "张三", note = "第1组")))
        assertTrue(repo.addMember("张三", "第1组") is LedgerRepository.RosterResult.Rejected)

        // 给出不同的区分信息（显式消歧）→ 允许
        assertTrue(repo.addMember("张三", "第2组") is LedgerRepository.RosterResult.Ok)
        val sameName = db.memberDao().findByName("张三")
        assertEquals(3, sameName.size)

        // 核对同名时必须消歧，绝不猜人
        val entries = sameName.map { it.toRosterEntry() }
        assertEquals(NameMatcher.MatchStatus.AMBIGUOUS, NameMatcher.match("张三", true, entries).status)
    }

    @Test
    fun `rename keeps member id and ledger name snapshot and writes audit`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("r1", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record
        val auditBefore = db.auditLogDao().all().size
        val member = db.memberDao().findByName("张三").first()

        val result = repo.renameMember(member.id, "张三丰", null)
        assertTrue(result is LedgerRepository.RosterResult.Ok)
        assertEquals(member.id, (result as LedgerRepository.RosterResult.Ok).member.id)

        // 名册已改名，member id 不变
        assertEquals("张三丰", db.memberDao().getById(member.id)!!.name)
        // 台账历史姓名快照保持不变
        val record = db.ledgerRecordDao().getById(confirmed.id)!!
        assertEquals(member.id, record.memberId)
        assertEquals("张三", record.memberName)
        // 审计新增
        assertEquals(auditBefore + 1, db.auditLogDao().all().size)
        assertTrue(db.auditLogDao().all().any { it.action == "MEMBER_RENAME" && it.entityId == member.id })
    }

    @Test
    fun `deactivate replaces delete and preserves row ledger and audit`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("r2", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record
        val member = db.memberDao().findByName("张三").first()
        val auditBefore = db.auditLogDao().all().size

        assertTrue(repo.setMemberActive(member.id, false) is LedgerRepository.RosterResult.Ok)

        // 行与 id 保留，只是停用（未删除）
        val row = db.memberDao().getById(member.id)
        assertNotNull(row)
        assertFalse(row!!.active)
        // 不再出现在可选名单里
        assertEquals(0, db.memberDao().allNames().size)
        // 台账与审计不受影响
        val record = db.ledgerRecordDao().getById(confirmed.id)!!
        assertEquals(member.id, record.memberId)
        assertEquals("张三", record.memberName)
        assertEquals(auditBefore + 1, db.auditLogDao().all().size)
        assertTrue(db.auditLogDao().all().any { it.action == "MEMBER_DEACTIVATE" && it.entityId == member.id })
    }

    // ---------- 单人录入 ----------

    @Test
    fun `single entry full flow writes ledger and audit log`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }

        val msg = repo.sendMessage("c1", "张三今天帮助同学", 1)
        repo.processMessage(msg.id)

        val cands = db.candidateEventDao().byMessage(msg.id)
        assertEquals(1, cands.size)
        assertEquals(ReviewStatus.READY_TO_CONFIRM, cands[0].status)

        val outcome = repo.confirmCandidate(cands[0].id)
        assertTrue(outcome is LedgerRepository.ConfirmOutcome.Confirmed)
        val record = (outcome as LedgerRepository.ConfirmOutcome.Confirmed).record
        assertEquals("张三", record.memberName)
        assertEquals(Category.REWARD.name, record.category)

        assertEquals(1, db.ledgerRecordDao().current().size)
        assertEquals(1, db.auditLogDao().all().size)
    }

    // ---------- 多人同句 ----------

    @Test
    fun `multi person sentence splits into separate records`() = runBlocking {
        seed("张三", "李四")
        gateway.structureResult = { _, _ -> listOf(raw("张三"), raw("李四", description = "拾金不昧")) }

        val msg = repo.sendMessage("c2", "张三帮助同学，李四拾金不昧", 1)
        repo.processMessage(msg.id)

        val cands = db.candidateEventDao().byMessage(msg.id)
        assertEquals(2, cands.size)
        repo.confirmCandidate(cands[0].id)
        repo.confirmCandidate(cands[1].id)
        assertEquals(2, db.ledgerRecordDao().current().size)
    }

    // ---------- 姓名不明确 ----------

    @Test
    fun `unknown name stays needs name and never writes ledger`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("路人甲")) }

        val msg = repo.sendMessage("c3", "路人甲帮助同学", 1)
        repo.processMessage(msg.id)

        val cand = db.candidateEventDao().byMessage(msg.id).first()
        assertEquals(ReviewStatus.NEEDS_NAME, cand.status)
        assertNull(cand.memberId)
        assertTrue(repo.confirmCandidate(cand.id) is LedgerRepository.ConfirmOutcome.NotReady)
        assertEquals(0, db.ledgerRecordDao().current().size)
    }

    // ---------- 幂等：重复发送 / 重试 ----------

    @Test
    fun `duplicate send with same client id is idempotent`() = runBlocking {
        seed("张三")
        val m1 = repo.sendMessage("same", "张三帮助同学", 1)
        val m2 = repo.sendMessage("same", "张三帮助同学", 2)
        assertEquals(m1.id, m2.id)
        assertEquals(1, db.chatMessageDao().byStates(listOf(ProcessState.PENDING.name)).size)
    }

    @Test
    fun `reprocessing a message does not duplicate candidates`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c4", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        repo.processMessage(msg.id)
        assertEquals(1, db.candidateEventDao().byMessage(msg.id).size)
    }

    @Test
    fun `confirm is idempotent no double ledger`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c5", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        repo.confirmCandidate(cand.id)
        repo.confirmCandidate(cand.id)
        assertEquals(1, db.ledgerRecordDao().current().size)
    }

    // ---------- 更正：保留原记录 + 审计 + 改归属 + 描述校验 ----------

    @Test
    fun `correction preserves original and writes audit relation`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c6", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record

        val member = db.memberDao().all().first()
        repo.correctRecord(confirmed.id, member.id, Category.PENALTY, "上课迟到")

        val all = db.ledgerRecordDao().all()
        assertEquals(2, all.size)
        val old = all.first { it.id == confirmed.id }
        assertEquals(LedgerStatus.SUPERSEDED, old.recordStatus)
        val current = all.first { it.status == LedgerStatus.CURRENT.name }
        assertEquals(old.id, current.supersedesId)
        assertEquals(2, current.revision)
        assertEquals("上课迟到", current.description)

        val logs = db.auditLogDao().all()
        assertEquals(2, logs.size)
        assertTrue(logs.any { it.action == "CONFIRM" })
        assertTrue(logs.any { it.action == "CORRECT" })
    }

    @Test
    fun `correction can reassign record to another member`() = runBlocking {
        seed("张三", "李四")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c7", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record

        val lisi = db.memberDao().findByName("李四").first()
        val outcome = repo.correctRecord(confirmed.id, lisi.id, Category.REWARD, "帮助同学")
        assertTrue(outcome is LedgerRepository.CorrectOutcome.Corrected)
        val current = (outcome as LedgerRepository.CorrectOutcome.Corrected).record
        assertEquals(lisi.id, current.memberId)
        assertEquals("李四", current.memberName)

        // 原记录保留但已作废，且仍指向张三
        val old = db.ledgerRecordDao().getById(confirmed.id)!!
        assertEquals(LedgerStatus.SUPERSEDED, old.recordStatus)
        assertEquals("张三", old.memberName)
    }

    @Test
    fun `correction rejects blank description`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c8", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record

        val member = db.memberDao().all().first()
        val outcome = repo.correctRecord(confirmed.id, member.id, Category.REWARD, "   ")
        assertTrue(outcome is LedgerRepository.CorrectOutcome.Error)
        // 无新记录产生
        assertEquals(1, db.ledgerRecordDao().all().size)
    }

    @Test
    fun `audit json escaping round trips special characters`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c9", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record

        val member = db.memberDao().all().first()
        val special = "帮助同学\"搬书\"\\路径\n第二行"
        repo.correctRecord(confirmed.id, member.id, Category.REWARD, special)

        val correctLog = db.auditLogDao().all().first { it.action == "CORRECT" }
        val snapshot = JsonCodec.decodeSnapshot(correctLog.afterJson!!)
        assertNotNull(snapshot)
        assertEquals(special, snapshot!!.description)
        assertNotNull(correctLog.beforeJson)
        assertNotNull(JsonCodec.decodeSnapshot(correctLog.beforeJson!!))
    }

    // ---------- 恢复中断写入 ----------

    @Test
    fun `recover resets stale processing to pending`() = runBlocking {
        seed("张三")
        val msg = repo.sendMessage("c10", "张三帮助同学", 1)
        db.chatMessageDao().updateState(msg.id, ProcessState.PROCESSING.name)
        repo.recoverInterruptedWrites()
        val recovered = db.chatMessageDao().getById(msg.id)!!
        assertEquals(ProcessState.PENDING, recovered.state)
    }

    // ---------- 查询：确定性 + 同名消歧 ----------

    @Test
    fun `query result is deterministic and contains record id and name`() = runBlocking {
        seed("张三")
        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c11", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record

        val member = db.memberDao().all().first()
        val text = repo.summarizeRecords(member.id)
        assertTrue(text.contains("[记录#${confirmed.id}]"))
        assertTrue(text.contains("张三"))
        assertTrue(text.contains("帮助同学"))
    }

    @Test
    fun `same name query requires member id and never mixes`() = runBlocking {
        db.memberDao().insertAll(listOf(MemberEntity(name = "张三", note = "第1组"), MemberEntity(name = "张三", note = "第2组")))
        val members = db.memberDao().findByName("张三")
        assertEquals(2, members.size)

        gateway.structureResult = { _, _ -> listOf(raw("张三")) }
        val msg = repo.sendMessage("c12", "张三帮助同学", 1)
        repo.processMessage(msg.id)
        // 同名 → 需要追问，不猜测
        val cand = db.candidateEventDao().byMessage(msg.id).first()
        assertEquals(ReviewStatus.NEEDS_NAME, cand.status)

        // 用户选定第2组张三
        val second = members[1]
        val status = repo.clarifyCandidate(cand.id, memberId = second.id)
        assertEquals(ReviewStatus.READY_TO_CONFIRM, status)
        val confirmed = (repo.confirmCandidate(cand.id) as LedgerRepository.ConfirmOutcome.Confirmed).record
        assertEquals(second.id, confirmed.memberId)

        // 查询按内部 id 隔离，不混入第1组
        assertEquals(1, repo.ledgerForMember(second.id).size)
        assertEquals(0, repo.ledgerForMember(members[0].id).size)
    }

    // ---------- 一致性核查 ----------

    @Test
    fun `consistency check reports confirmed candidate missing ledger`() = runBlocking {
        seed("张三")
        val msg = repo.sendMessage("c15", "张三帮助同学", 1)
        val member = db.memberDao().all().first()
        db.candidateEventDao().insert(
            CandidateEventEntity(
                chatMessageId = msg.id,
                idempotencyKey = "c15#0",
                statedName = "张三",
                memberId = member.id,
                memberName = "张三",
                category = "REWARD",
                description = "帮助同学",
                reviewStatus = ReviewStatus.CONFIRMED.name,
                question = null,
            ),
        )
        val issues = repo.verifyConsistency()
        assertTrue(issues.any { it.contains("缺少对应台账记录") })
    }
}
