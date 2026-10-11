package com.enanke.awardledger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

/** 顶层导航目的地。 */
enum class Screen(val label: String, val icon: ImageVector) {
    Chat("聊天", Icons.Filled.Message),
    Pending("待处理", Icons.Filled.Pending),
    Confirm("待确认", Icons.Filled.CheckCircle),
    Correct("更正", Icons.Filled.Edit),
    Query("查询", Icons.Filled.Search),
    Ledger("台账", Icons.Filled.List),
    Roster("名册", Icons.Filled.People),
}
