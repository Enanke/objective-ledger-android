package com.enanke.awardledger.domain

import com.enanke.awardledger.domain.model.LedgerRecordDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryNumberingTest {

    private fun record(id: Long, name: String = "张三", cat: String = "REWARD", desc: String = "帮助同学") =
        LedgerRecordDto(id = id, name = name, category = cat, description = desc)

    @Test
    fun `deterministic list is numbered one per record`() {
        val text = QueryNumbering.buildDeterministic(listOf(record(1), record(2)))
        assertTrue(text.contains("1."))
        assertTrue(text.contains("2."))
        assertFalse(text.contains("3."))
    }

    @Test
    fun `each line contains real record id and name`() {
        val line = QueryNumbering.formatRecord(0, record(id = 42, name = "张三"))
        assertTrue(line.contains("[记录#42]"))
        assertTrue(line.contains("张三"))
        assertTrue(line.contains("奖"))
        assertTrue(line.contains("帮助同学"))
    }

    @Test
    fun `penalty label renders correctly`() {
        val line = QueryNumbering.formatRecord(0, record(id = 7, cat = "PENALTY"))
        assertTrue(line.contains("惩"))
    }
}
