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
import com.enanke.awardledger.domain.TimeFormat
import com.enanke.awardledger.ui.viewmodel.LedgerViewModel

@Composable
fun LedgerScreen(container: AppContainer) {
    val vm: LedgerViewModel = viewModel { LedgerViewModel(container.repository) }
    val records by vm.records.collectAsStateWithLifecycle()
    val audit by vm.auditLog.collectAsStateWithLifecycle()
    var showAudit by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("台账（${records.size} 条）", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { vm.checkConsistency() }) { Text("核查") }
            Button(onClick = { vm.recover() }, modifier = Modifier.padding(start = 8.dp)) { Text("恢复") }
            TextButton(onClick = { showAudit = !showAudit }) { Text(if (showAudit) "看台账" else "看审计") }
        }

        if (vm.consistencyIssues.isNotEmpty()) {
            Text(
                "核查异常：\n" + vm.consistencyIssues.joinToString("\n"),
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (showAudit) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(audit, key = { it.id }) { a ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${TimeFormat.format(a.timestampMillis)}  ${a.action}")
                            Text("${a.entityType}#${a.entityId}")
                            a.afterJson?.let { Text("after: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(records, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${r.memberName} · ${r.categoryEnum.label} · rev${r.revision}")
                            Text(r.description)
                        }
                    }
                }
            }
        }
    }
}
