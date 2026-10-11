package com.enanke.awardledger.domain

import com.enanke.awardledger.domain.model.RawCandidate
import com.enanke.awardledger.domain.model.ReviewStatus
import com.enanke.awardledger.domain.model.RosterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureValidatorTest {

    private val roster = listOf(
        RosterEntry(1, "张三"),
        RosterEntry(2, "李四"),
        RosterEntry(3, "王五", "第1组"),
        RosterEntry(4, "王五", "第2组"),
    )

    private fun raw(
        name: String,
        explicit: Boolean = true,
        category: String = "REWARD",
        description: String = "帮助同学搬书",
        question: String = "",
    ) = RawCandidate(name, explicit, category, description, question)

    @Test
    fun `single valid candidate is ready with member id`() {
        val v = StructureValidator.validateOne(raw("张三"), roster)
        assertEquals(ReviewStatus.READY_TO_CONFIRM, v.status)
        assertEquals(1L, v.resolvedMemberId)
        assertEquals("张三", v.resolvedName)
    }

    @Test
    fun `multi person same sentence produces one candidate each`() {
        val list = StructureValidator.validate(listOf(raw("张三"), raw("李四")), roster)
        assertEquals(2, list.size)
        assertEquals(ReviewStatus.READY_TO_CONFIRM, list[0].status)
        assertEquals(ReviewStatus.READY_TO_CONFIRM, list[1].status)
        assertEquals(1L, list[0].resolvedMemberId)
        assertEquals(2L, list[1].resolvedMemberId)
    }

    @Test
    fun `unknown name needs name`() {
        val v = StructureValidator.validateOne(raw("路人甲"), roster)
        assertEquals(ReviewStatus.NEEDS_NAME, v.status)
        assertTrue(v.message!!.contains("不在名单"))
    }

    @Test
    fun `non explicit name needs name even if guessed`() {
        val v = StructureValidator.validateOne(raw("张三", explicit = false), roster)
        assertEquals(ReviewStatus.NEEDS_NAME, v.status)
    }

    @Test
    fun `ambiguous duplicate name needs name with disambiguation hint`() {
        val v = StructureValidator.validateOne(raw("王五"), roster)
        assertEquals(ReviewStatus.NEEDS_NAME, v.status)
        assertNotNull(v.message)
        assertTrue(v.message!!.contains("同名"))
        assertTrue(v.message!!.contains("第1组"))
        assertTrue(v.message!!.contains("第2组"))
    }

    @Test
    fun `missing category needs category`() {
        assertEquals(ReviewStatus.NEEDS_CATEGORY, StructureValidator.validateOne(raw("张三", category = ""), roster).status)
    }

    @Test
    fun `missing description needs description`() {
        assertEquals(ReviewStatus.NEEDS_DESCRIPTION, StructureValidator.validateOne(raw("张三", description = "  "), roster).status)
    }
}
