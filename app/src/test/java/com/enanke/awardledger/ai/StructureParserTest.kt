package com.enanke.awardledger.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureParserTest {

    @Test
    fun `parses plain json array`() {
        val json = """
            {"candidates":[{"name":"张三","nameExplicit":true,"category":"REWARD","description":"帮助同学","question":""}]}
        """.trimIndent()
        val result = StructureParser.parseCandidates(json)
        assertEquals(1, result.size)
        assertEquals("张三", result[0].name)
        assertTrue(result[0].nameExplicit)
        assertEquals("REWARD", result[0].categoryCode)
    }

    @Test
    fun `parses markdown fenced json`() {
        val json = """
            以下是解析结果：
            ```json
            {"candidates":[{"name":"李四","category":"PENALTY","description":"上课迟到"}]}
            ```
        """.trimIndent()
        val result = StructureParser.parseCandidates(json)
        assertEquals(1, result.size)
        assertEquals("李四", result[0].name)
        assertEquals("PENALTY", result[0].categoryCode)
    }

    @Test
    fun `missing fields get defaults`() {
        val result = StructureParser.parseCandidates("""{"candidates":[{}]}""")
        assertEquals(1, result.size)
        assertEquals("", result[0].name)
        assertEquals("", result[0].description)
    }

    @Test
    fun `blank input returns empty`() {
        assertTrue(StructureParser.parseCandidates(null).isEmpty())
        assertTrue(StructureParser.parseCandidates("").isEmpty())
    }

    @Test
    fun `garbage input returns empty`() {
        assertTrue(StructureParser.parseCandidates("这不是 JSON").isEmpty())
    }
}
