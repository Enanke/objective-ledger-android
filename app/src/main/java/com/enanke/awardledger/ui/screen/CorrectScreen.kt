package com.enanke.awardledger.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enanke.awardledger.core.AppContainer
import com.enanke.awardledger.data.LedgerRecordEntity
import com.enanke.awardledger.domain.model.Category
import com.enanke.awardledger.ui.viewmodel.CorrectViewModel
import com.enanke.awardledger.ui.viewmodel.CorrectionResult

@Composable
fun CorrectScreen(container: AppContainer) {
    val vm: CorrectViewModel = viewModel { CorrectViewModel(container.repository) }
    val records by vm.records.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LedgerRecordEntity?>(null) }

    val result = vm.correctionResult.value
    // 仅当更正成功时才关闭弹窗；失败则保留弹窗并展示错误。
    LaunchedEffect(result) {
        if (result is CorrectionResult.Success) {
            selected = null
            vm.clearResult()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("台账记录（点击“更正”保留原记录并生成新记录）", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxSize()) {
            items(records, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.memberName} · ${r.categoryEnum.label} · rev${r.revision}")
                            Text(r.description)
                        }
                        TextButton(onClick = { vm.clearResult(); selected = r }) { Text("更正") }
                    }
                }
            }
        }
    }

    selected?.let { r ->
        CorrectDialog(vm, r, result, onDismiss = { selected = null; vm.clearResult() })
    }
}

@Composable
private fun CorrectDialog(
    vm: CorrectViewModel,
    r: LedgerRecordEntity,
    result: CorrectionResult?,
    onDismiss: () -> Unit,
) {
    val members by vm.members.collectAsStateWithLifecycle()
    var selectedMemberId by remember(r.id) { mutableStateOf(r.memberId) }
    var category by remember(r.id) { mutableStateOf(r.categoryEnum) }
    var description by remember(r.id) { mutableStateOf(r.description) }
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedLabel = members.firstOrNull { it.id == selectedMemberId }?.toRosterEntry()?.display() ?: "选择成员"
    val errorMessage = (result as? CorrectionResult.Failure)?.message

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更正记录 #${r.id}") },
        text = {
            Column {
                Text("更正会保留原记录并写入新记录，审计日志同事务记录；可把记录归属改为另一成员。")

                Box {
                    OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("归属成员：$selectedLabel")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.toRosterEntry().display()) },
                                onClick = { selectedMemberId = m.id; menuExpanded = false },
                            )
                        }
                    }
                }

                Row {
                    FilterChip(selected = category == Category.REWARD, onClick = { category = Category.REWARD }, label = { Text("奖") })
                    FilterChip(
                        selected = category == Category.PENALTY,
                        onClick = { category = Category.PENALTY },
                        label = { Text("惩") },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("事迹") })
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.applyCorrection(r.id, selectedMemberId, category, description) }) { Text("确认更正") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
