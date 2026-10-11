package com.enanke.awardledger.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enanke.awardledger.core.AppContainer
import com.enanke.awardledger.ui.viewmodel.AssistantKind
import com.enanke.awardledger.ui.viewmodel.ChatItem
import com.enanke.awardledger.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(container: AppContainer) {
    val vm: ChatViewModel = viewModel { ChatViewModel(container.repository, container.connectivity) }
    val items by vm.chatItems.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 顶部左侧：状态行 + 署名小字。
            // 署名是普通布局内容（非悬浮/非覆盖），放进本行左侧列内复用该行已由右侧按钮确定的纵向空间，
            // 因此不额外占用高度，消息列表与输入框不受影响。
            Column(Modifier.weight(1f)) {
                Text(if (online) "状态：在线（自动解析）" else "状态：离线（已排队待处理）")
                Text(
                    text = "由谙那刻独立开发",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!online) TextButton(onClick = { vm.retryPending() }) { Text("重试待处理") }
            TextButton(onClick = { showSettings = true }) { Text("连接设置") }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.User -> UserBubble(item)
                    is ChatItem.Assistant -> AssistantBubble(item)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("像发消息一样描述事迹…") },
            )
            Button(
                onClick = { vm.send(input) { input = "" } },
                enabled = !sending,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(if (sending) "发送中…" else "发送")
            }
        }
    }

    if (showSettings) {
        AiSettingsDialog(container, onDismiss = { showSettings = false })
    }
}

@Composable
private fun UserBubble(item: ChatItem.User) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text("班干部", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Text(item.message.rawText)
        }
    }
}

@Composable
private fun AssistantBubble(item: ChatItem.Assistant) {
    val color = when (item.kind) {
        AssistantKind.QUESTION -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
        AssistantKind.ERROR -> androidx.compose.material3.MaterialTheme.colorScheme.error
        AssistantKind.SUMMARY -> androidx.compose.material3.MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(item.text, Modifier.padding(10.dp), color = color)
    }
}
