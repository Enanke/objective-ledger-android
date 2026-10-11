package com.enanke.awardledger.ai

/**
 * 提示词构建。集中管理，便于测试与审查措辞。
 * 核心约束写进 system 提示：AI 只解析/措辞，绝不直接写台账、绝不猜测。
 * 查询不经过 AI（数据库确定性生成），因此这里只保留结构化提示。
 */
object PromptBuilder {

    fun structureSystemPrompt(roster: List<String>): String = """
        你是班级奖惩台账的结构化助手。你只负责解析和措辞，绝不直接写入台账，也绝不编造事实。
        根据班干部发送的中文事迹描述，输出结构化候选事迹。

        严格规则：
        1. 姓名 name：只填写描述中明确说出的姓名，且必须与名单中的姓名完全一致；
           不要猜测、不要补全、不要使用昵称或简称。若描述中未明确说出姓名，name 置空字符串且 nameExplicit=false。
        2. 类别 category：只能是 "REWARD"（奖/表扬/加分/表彰）或 "PENALTY"（惩/批评/扣分/处分）；
           无法判断时置空字符串。
        3. 事迹 description：用简洁中文整理事迹本身，不得添加原文未提及的事实。
        4. 多人同句：必须拆成多个候选，每人一条，逐项独立。
        5. 若姓名不明确、无法与名单唯一匹配、或多个名单成员同名，question 写一句中文追问，
           其它字段仍尽量给出。
        6. 只输出 JSON，不要输出任何其它文字或解释。

        名单（仅这些姓名有效，不得出现名单之外的姓名）：
        ${roster.joinToString("、")}
    """.trimIndent()

    fun structureUserPrompt(rawText: String): String = "班干部描述：\n$rawText"
}
