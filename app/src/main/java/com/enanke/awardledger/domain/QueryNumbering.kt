package com.enanke.awardledger.domain

import com.enanke.awardledger.domain.model.LedgerRecordDto

/**
 * 查询编号与“逐条对应”保证。
 *
 * 查询结果完全由数据库确定性生成：每行包含真实记录 ID、姓名、类别、描述，
 * 并附编号 1..N。查询不经过 AI，从根本上杜绝模型编造或张冠李戴。
 */
object QueryNumbering {

    fun categoryLabel(category: String): String =
        if (category.equals("REWARD", ignoreCase = true)) "奖" else "惩"

    /** 每行含：编号 + 真实记录 ID + 姓名 + 类别 + 描述。 */
    fun formatRecord(index: Int, r: LedgerRecordDto): String =
        "${index + 1}. [记录#${r.id}] ${r.name} · ${categoryLabel(r.category)}：${r.description}"

    fun buildDeterministic(records: List<LedgerRecordDto>): String =
        records.mapIndexed { i, r -> formatRecord(i, r) }.joinToString("\n")
}
