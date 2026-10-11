package com.enanke.awardledger.domain.model

/**
 * 奖/惩类别。AI 输出使用大写英文码，也兼容常见中文标签。
 * 全应用绝不使用学号，仅使用姓名 + 内部成员 id 作为唯一标识。
 */
enum class Category(val label: String) {
    REWARD("奖"),
    PENALTY("惩");

    companion object {
        fun fromCode(code: String?): Category? {
            if (code == null) return null
            return when (code.trim().uppercase()) {
                "REWARD", "奖", "奖励", "表扬", "加分", "表彰" -> REWARD
                "PENALTY", "惩", "惩罚", "批评", "扣分", "处分" -> PENALTY
                else -> null
            }
        }
    }
}

/** 候选事迹的复核状态：决定它出现在“待确认”界面以及需要追问什么。 */
enum class ReviewStatus {
    /** 姓名唯一匹配且字段齐全，可入账。 */
    READY_TO_CONFIRM,
    NEEDS_NAME,
    NEEDS_CATEGORY,
    NEEDS_DESCRIPTION,
    CONFIRMED,
    DISCARDED
}

/** 聊天消息的处理阶段。 */
enum class ProcessState {
    /** 已持久化原话，尚未结构化（离线排队）。 */
    PENDING,
    /** 正在调用 AI 结构化（瞬态，可被恢复重置）。 */
    PROCESSING,
    /** 已生成候选，等待人工确认。 */
    PARSED,
    /** 全部候选已确认/丢弃，流程结束。 */
    DONE,
    /** AI 调用失败，可重试。 */
    FAILED
}

/** 台账记录状态：更正时保留原记录并标记为 SUPERSEDED，新记录为 CURRENT。 */
enum class LedgerStatus {
    CURRENT,
    SUPERSEDED
}

/**
 * 名单条目（用于姓名核对与同名消歧）。
 * [id] 是内部成员 id，[note] 是区分信息（同名时区分，绝不用学号）。
 */
data class RosterEntry(
    val id: Long,
    val name: String,
    val note: String? = null,
) {
    fun display(): String = note?.let { "$name（$it）" } ?: name
}

/**
 * AI 结构化的原始候选（未经本地核对）。字段尽量原始，交由本地确定性逻辑校验。
 */
data class RawCandidate(
    /** 描述中明确说出的姓名，未说出则为空串。 */
    val name: String,
    /** AI 是否认为姓名在原文中被明确说出（false 则即便有 name 也视为猜测，绝不用）。 */
    val nameExplicit: Boolean,
    /** 类别原始码（REWARD/PENALTY/中文），可能为空。 */
    val categoryCode: String,
    /** 整理后的事迹描述。 */
    val description: String,
    /** AI 给出的追问，用于姓名不明确/字段缺失等场景。 */
    val question: String,
)

/** 本地核对后的候选：携带解析/匹配结果。 */
data class ValidatedCandidate(
    val raw: RawCandidate,
    val status: ReviewStatus,
    val resolvedName: String?,
    /** 唯一匹配时的内部成员 id；同名/不明确时为 null。 */
    val resolvedMemberId: Long?,
    val resolvedCategory: Category?,
    /** 面向用户的追问文案（可为空）。 */
    val message: String?,
)

/** 传给查询的台账记录快照（只读、不含内部关系，但含真实记录 ID 与姓名）。 */
data class LedgerRecordDto(
    val id: Long,
    val name: String,
    val category: String,
    val description: String,
)
