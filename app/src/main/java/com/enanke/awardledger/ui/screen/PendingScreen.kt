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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enanke.awardledger.core.AppContainer
import com.enanke.awardledger.domain.TimeFormat
import com.enanke.awardledger.domain.model.ProcessState
import com.enanke.awardledger.ui.viewmodel.PendingViewModel

@Composable
fun PendingScreen(container: AppContainer) {
    val vm: PendingViewModel = viewModel { PendingViewModel(container.repository) }
    val pending by vm.pending.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("待处理（离线/失败，共 ${pending.size} 条）", Modifier.weight(1f))
            Button(onClick = { vm.retry() }, enabled = pending.isNotEmpty()) { Text("立即处理") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(pending) { m ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("发送时间：${TimeFormat.format(m.sentAtMillis)}")
                        Text("原话：${m.rawText}")
                        Text(if (m.state == ProcessState.FAILED) "状态：解析失败（可重试）" else "状态：待处理")
                    }
                }
            }
        }
    }
}
