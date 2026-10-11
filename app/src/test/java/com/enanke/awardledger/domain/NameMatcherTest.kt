package com.enanke.awardledger.domain

import com.enanke.awardledger.domain.model.RosterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    private val uniqueRoster = listOf(
        RosterEntry(1, "张三"),
        RosterEntry(2, "李四"),
        RosterEntry(3, "王五"),
    )

    private val dupRoster = listOf(
        RosterEntry(1, "张三", "第1组"),
        RosterEntry(2, "张三", "第2组"),
        RosterEntry(3, "李四"),
    )

    @Test
    fun `exact unique match resolves name and member id`() {
        val r = NameMatcher.match("张三", true, uniqueRoster)
        assertEquals(NameMatcher.MatchStatus.UNIQUE, r.status)
        assertEquals("张三", r.resolvedName)
        assertEquals(1L, r.resolvedMemberId)
    }

    @Test
    fun `empty name is missing`() {
        assertEquals(NameMatcher.MatchStatus.MISSING, NameMatcher.match("", true, uniqueRoster).status)
        assertEquals(NameMatcher.MatchStatus.MISSING, NameMatcher.match("  ", true, uniqueRoster).status)
    }

    @Test
    fun `not explicit is missing even if name guessed`() {
        val r = NameMatcher.match("张三", false, uniqueRoster)
        assertEquals(NameMatcher.MatchStatus.MISSING, r.status)
        assertNull(r.resolvedName)
    }

    @Test
    fun `nickname or partial is never matched`() {
        assertEquals(NameMatcher.MatchStatus.NOT_FOUND, NameMatcher.match("小张", true, uniqueRoster).status)
        assertEquals(NameMatcher.MatchStatus.NOT_FOUND, NameMatcher.match("张", true, uniqueRoster).status)
        assertEquals(NameMatcher.MatchStatus.NOT_FOUND, NameMatcher.match("张三同学", true, uniqueRoster).status)
    }

    @Test
    fun `duplicate names are ambiguous with all entries returned`() {
        val r = NameMatcher.match("张三", true, dupRoster)
        assertEquals(NameMatcher.MatchStatus.AMBIGUOUS, r.status)
        assertNull(r.resolvedName)
        assertNull(r.resolvedMemberId)
        assertEquals(2, r.matchingEntries.size)
        assertTrue(r.matchingEntries.map { it.id }.containsAll(listOf(1L, 2L)))
    }
}
