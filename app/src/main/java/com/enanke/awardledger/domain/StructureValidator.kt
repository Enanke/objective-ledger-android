package com.enanke.awardledger.domain

import com.enanke.awardledger.domain.model.Category
import com.enanke.awardledger.domain.model.RawCandidate
import com.enanke.awardledger.domain.model.ReviewStatus
import com.enanke.awardledger.domain.model.RosterEntry
import com.enanke.awardledger.domain.model.ValidatedCandidate

/**
 * 姓名核对器：唯一权威。规则是“完全匹配唯一姓名”，绝不猜测、绝不模糊匹配、绝不补全。
 *
 * - 空姓名 或 AI 未明确说出 -> MISSING（追问姓名）。
 * - 恰好一个名单成员完全相等 -> UNIQUE（可入账，携带内部成员 id）。
 * - 多个名单成员同名 -> AMBIGUOUS（返回全部同名条目，供用户按区分信息选择）。
 * - 无任何名单成员完全相等 -> NOT_FOUND（追问，昵称/错字/简称一律不猜）。
 */
object NameMatcher {

    enum class MatchStatus { UNIQUE, AMBIGUOUS, NOT_FOUND, MISSING }

    data class MatchResult(
        val status: MatchStatus,
        val resolvedName: String? = null,
        val resolvedMemberId: Long? = null,
        val matchingEntries: List<RosterEntry> = emptyList(),
    ) {
        companion object {
            fun unique(entry: RosterEntry) = MatchResult(
                status = MatchStatus.UNIQUE,
                resolvedName = entry.name,
                resolvedMemberId = entry.id,
                matchingEntries = listOf(entry),
            )
            fun notFound() = MatchResult(MatchStatus.NOT_FOUND)
            fun missing() = MatchResult(MatchStatus.MISSING)
            fun ambiguous(entries: List<RosterEntry>) =
                MatchResult(MatchStatus.AMBIGUOUS, matchingEntries = entries)
        }
    }

    fun match(statedName: String, explicit: Boolean, roster: List<RosterEntry>): MatchResult {
        if (!explicit) return MatchResult.missing()
        val trimmed = statedName.trim()
        if (trimmed.isEmpty()) return MatchResult.missing()
        val hits = roster.filter { it.name == trimmed }
        return when {
            hits.size == 1 -> MatchResult.unique(hits.first())
            hits.size > 1 -> MatchResult.ambiguous(hits)
            else -> MatchResult.notFound()
        }
    }
}

/**
 * 结构校验器：把 AI 输出候选逐项核对，得到待确认状态与追问文案。
 * 这里只做“判断”，不写库；写库由 Repository 在事务里完成。
 * 事迹不再包含发生日期/时间，仅核对姓名、类别、描述。
 */
object StructureValidator {

    fun validate(raw: List<RawCandidate>, roster: List<RosterEntry>): List<ValidatedCandidate> =
        raw.map { validateOne(it, roster) }

    fun validateOne(c: RawCandidate, roster: List<RosterEntry>): ValidatedCandidate {
        val nameResult = NameMatcher.match(c.name, c.nameExplicit, roster)
        val category = Category.fromCode(c.categoryCode)
        val descBlank = c.description.isBlank()

        val status = when {
            nameResult.status != NameMatcher.MatchStatus.UNIQUE -> ReviewStatus.NEEDS_NAME
            category == null -> ReviewStatus.NEEDS_CATEGORY
            descBlank -> ReviewStatus.NEEDS_DESCRIPTION
            else -> ReviewStatus.READY_TO_CONFIRM
        }

        val message = buildMessage(nameResult, c, status)

        return ValidatedCandidate(
            raw = c,
            status = status,
            resolvedName = nameResult.resolvedName,
            resolvedMemberId = nameResult.resolvedMemberId,
            resolvedCategory = category,
            message = message,
        )
    }

    private fun buildMessage(
        nameResult: NameMatcher.MatchResult,
        c: RawCandidate,
        status: ReviewStatus,
    ): String? {
        val aiQuestion = c.question.trim()
        return when (status) {
            ReviewStatus.NEEDS_NAME -> when (nameResult.status) {
                NameMatcher.MatchStatus.MISSING -> aiQuestion.ifBlank { "请问这件事是哪个同学？请给出名单里的完整姓名。" }
                NameMatcher.MatchStatus.AMBIGUOUS -> aiQuestion.ifBlank {
                    val opts = nameResult.matchingEntries.joinToString("；") { it.display() }
                    "名单中有同名同学：$opts。请选择具体是哪一位。"
                }
                NameMatcher.MatchStatus.NOT_FOUND -> aiQuestion.ifBlank { "“${c.name.trim()}”不在名单中，请确认该同学的准确姓名。" }
                else -> null
            }
            ReviewStatus.NEEDS_CATEGORY -> aiQuestion.ifBlank { "这件事属于“奖”还是“惩”？请说明。" }
            ReviewStatus.NEEDS_DESCRIPTION -> aiQuestion.ifBlank { "请补充具体事迹内容。" }
            else -> null
        }
    }
}
