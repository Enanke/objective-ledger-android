package com.enanke.awardledger.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.enanke.awardledger.data.MemberEntity
import com.enanke.awardledger.ui.viewmodel.RosterViewModel

/**
 * 名册管理：批量导入/补充、单独新增、改名、停用/启用。
 * 名册只保存在本机 Room 数据库；新安装为空名册。
 */
@Composable
fun RosterScreen(container: AppContainer) {
    val vm: RosterViewModel = viewModel { RosterViewModel(container.repository) }
    val members by vm.members.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<MemberEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("名册管理", style = MaterialTheme.typography.titleMedium)
        Text(
            "共 ${members.size} 人 · 启用 ${members.count { it.active }} 人 · 名册只存在本机数据库",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = vm.importText,
            onValueChange = { vm.importText = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("批量导入 / 补充：每行一个姓名，可直接粘贴") },
            minLines = 2,
        )
        Button(onClick = { vm.import() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("导入")
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.newName,
                onValueChange = { vm.newName = it },
                modifier = Modifier.weight(1f),
                label = { Text("姓名") },
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.newNote,
                onValueChange = { vm.newNote = it },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                label = { Text("区分信息") },
                singleLine = true,
            )
        }
        Button(onClick = { vm.add() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("新增成员")
        }

        vm.message?.let { msg ->
            Text(
                msg,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            items(members, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name + (m.note?.let { "（$it）" } ?: ""))
                            Text(
                                if (m.active) "启用中" else "已停用（保留 id 与历史）",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (m.active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        TextButton(onClick = { renameTarget = m }) { Text("改名") }
                        TextButton(onClick = { vm.setActive(m.id, !m.active) }) {
                            Text(if (m.active) "停用" else "启用")
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            member = target,
            onDismiss = { renameTarget = null },
            onConfirm = { name, note ->
                vm.rename(target.id, name, note)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun RenameDialog(
    member: MemberEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var name by remember(member.id) { mutableStateOf(member.name) }
    var note by remember(member.id) { mutableStateOf(member.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改名（member id 与台账历史不变）") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("姓名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("区分信息（与现有同名成员冲突时必填）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, note.ifBlank { null }) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
