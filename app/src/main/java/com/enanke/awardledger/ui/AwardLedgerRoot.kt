package com.enanke.awardledger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.enanke.awardledger.core.AppContainer
import com.enanke.awardledger.ui.screen.ChatScreen
import com.enanke.awardledger.ui.screen.ConfirmScreen
import com.enanke.awardledger.ui.screen.CorrectScreen
import com.enanke.awardledger.ui.screen.LedgerScreen
import com.enanke.awardledger.ui.screen.PendingScreen
import com.enanke.awardledger.ui.screen.QueryScreen
import com.enanke.awardledger.ui.screen.RosterScreen

@Composable
fun AwardLedgerRoot(container: AppContainer) {
    // 顺序初始化：名册不再种子化/重置（新装为空，已有数据库不触碰），
    // 因此这里只做“恢复中断写入”，随后订阅联网自动处理待办。
    LaunchedEffect(Unit) {
        container.repository.ensureInitialized()
        container.repository.recoverInterruptedWrites()
        container.connectivity.online.collect { online ->
            if (online) container.repository.processPending()
        }
    }

    var current by rememberSaveable { mutableStateOf(Screen.Chat.name) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = current == screen.name,
                        onClick = { current = screen.name },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Screen.valueOf(current)) {
                Screen.Chat -> ChatScreen(container)
                Screen.Pending -> PendingScreen(container)
                Screen.Confirm -> ConfirmScreen(container)
                Screen.Correct -> CorrectScreen(container)
                Screen.Query -> QueryScreen(container)
                Screen.Ledger -> LedgerScreen(container)
                Screen.Roster -> RosterScreen(container)
            }
        }
    }
}
