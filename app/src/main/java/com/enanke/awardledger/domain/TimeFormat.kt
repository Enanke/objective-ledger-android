package com.enanke.awardledger.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间显示工具（与解析分开，避免语义混淆）。 */
object TimeFormat {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun format(epochMillis: Long): String = fmt.format(Date(epochMillis))
}
