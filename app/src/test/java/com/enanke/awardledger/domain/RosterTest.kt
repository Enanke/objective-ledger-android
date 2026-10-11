// 文件名沿用 RosterTest.kt；类名 RosterImportTest 对应现在的纯文本名册导入逻辑。
package com.enanke.awardledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RosterImportTest {

    @Test
    fun `normalizeName trims blanks and rejects empty`() {
        assertEquals("张三", RosterImport.normalizeName("  张三  "))
        assertEquals("张三", RosterImport.normalizeName("　张三　")) // 全角空格
        assertNull(RosterImport.normalizeName(""))
        assertNull(RosterImport.normalizeName("   "))
        assertNull(RosterImport.normalizeName("　"))
    }

    @Test
    fun `parseNames splits lines ignores blanks and dedupes`() {
        val raw = "张三\n\n  李四  \n王五\r\n张三\n"
        assertEquals(listOf("张三", "李四", "王五"), RosterImport.parseNames(raw))
    }

    @Test
    fun `parseNames accepts comma and enumeration separators`() {
        assertEquals(listOf("张三", "李四", "王五"), RosterImport.parseNames("张三, 李四、王五"))
    }

    @Test
    fun `countBlankItems reports ignored blanks`() {
        assertEquals(3, RosterImport.countBlankItems("张三\n\n   \n李四\n"))
    }

    @Test
    fun `blank input yields empty list`() {
        assertEquals(emptyList<String>(), RosterImport.parseNames(""))
        assertEquals(emptyList<String>(), RosterImport.parseNames("\n \n"))
    }
}
