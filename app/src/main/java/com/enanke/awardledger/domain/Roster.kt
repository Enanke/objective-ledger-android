package com.enanke.awardledger.domain

/**
 * 名册导入与姓名规范化（纯函数，便于单测）。
 *
 * - **不内置任何姓名**：新安装名册为空，全部由用户自行录入。
 * - **不使用学号 / 名单序号**：成员只用姓名 + 内部 id 标识。
 */
object RosterImport {

    /** 批量导入时可识别的分隔符：换行、制表、半角/全角逗号、顿号、分号。 */
    private val SEPARATORS = charArrayOf(
        '\n', '\r', '\t', ',', '，', '、', ';', '；',
    )

    /**
     * 规范化单个姓名：去掉首尾空格（含全角空格等空白字符）。
     * 结果为空则返回 `null`（调用方据此拒绝或忽略）。
     */
    fun normalizeName(raw: String): String? {
        val t = raw.trim()
        return if (t.isEmpty()) null else t
    }

    /**
     * 解析批量文本为姓名列表：按分隔符拆分、逐个去空白、**忽略空行**、
     * 并对输入内部重复项去重（保持首次出现顺序）。
     */
    fun parseNames(raw: String): List<String> =
        raw.split(*SEPARATORS)
            .mapNotNull { normalizeName(it) }
            .distinct()

    /** 统计输入中被忽略的空项数量（便于向用户说明）。 */
    fun countBlankItems(raw: String): Int =
        raw.split(*SEPARATORS).count { normalizeName(it) == null }
}
