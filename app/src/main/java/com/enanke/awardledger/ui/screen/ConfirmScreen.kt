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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enanke.awardledger.core.AppContainer
import com.enanke.awardledger.data.CandidateEventEntity
import com.enanke.awardledger.data.MemberEntity
import com.enanke.awardledger.domain.model.Category
import com.enanke.awardledger.domain.model.ReviewStatus
import com.enanke.awardledger.ui.viewmodel.ConfirmViewModel

@Composable
fun ConfirmScreen(container: AppContainer) {
    val vm: ConfirmViewModel = viewModel { ConfirmViewModel(container.repository) }
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Text("待确认（共 ${candidates.size} 条）", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxSize()) {
            items(candidates, key = { it.id }) { c ->
                CandidateCard(
                    candidate = c,
                    members = members,
                    onConfirm = { vm.confirm(c.id) },
                    onDiscard = { vm.discard(c.id) },
                    onClarify = { memberId, cat, desc ->
                        vm.clarify(c.id, memberId, null, cat, desc)
                    },
                )
            }
        }
    }
}

private fun statusLabel(status: ReviewStatus): String = when (status) {
    ReviewStatus.READY_TO_CONFIRM -> "可入账"
    ReviewStatus.NEEDS_NAME -> "待补姓名"
    ReviewStatus.NEEDS_CATEGORY -> "待补类别"
    ReviewStatus.NEEDS_DESCRIPTION -> "待补事迹"
    else -> status.name
}

@Composable
private fun CandidateCard(
    candidate: CandidateEventEntity,
    members: List<MemberEntity>,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onClarify: (memberId: Long?, categoryCode: String?, description: String?) -> Unit,
) {
    var expanded by remember(candidate.id) { mutableStateOf(false) }
    var selectedMemberId by remember(candidate.id) { mutableStateOf<Long?>(candidate.memberId) }
    var category by remember(candidate.id) { mutableStateOf(candidate.categoryEnum) }
    var description by remember(candidate.id) { mutableStateOf(candidate.description ?: "") }

    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(statusLabel(candidate.status), color = MaterialTheme.colorScheme.primary)
            Text("姓名：${candidate.memberName ?: candidate.statedName ?: "（待确认）"}")
            Text("类别：${candidate.categoryEnum?.label ?: "（待确认）"}")
            Text("事迹：${candidate.description ?: "（待补充）"}")
            candidate.question?.takeIf { it.isNotBlank() && candidate.status != ReviewStatus.READY_TO_CONFIRM }?.let {
                Text(it, color = MaterialTheme.colorScheme.tertiary)
            }

            if (candidate.status == ReviewStatus.READY_TO_CONFIRM) {
                Row {
                    Button(onClick = onConfirm) { Text("入账") }
                    OutlinedButton(onClick = onDiscard, modifier = Modifier.padding(start = 8.dp)) { Text("丢弃") }
                }
            } else {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起补充" else "补充 / 追问")
                }
                if (expanded) {
                    EditSection(
                        members = members,
                        selectedMemberId = selectedMemberId,
                        onSelectMember = { selectedMemberId = it },
                        category = category,
                        onCategory = { category = it },
                        description = description,
                        onDescription = { description = it },
                        onSubmit = {
                            onClarify(selectedMemberId, category?.name, description)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSection(
    members: List<MemberEntity>,
    selectedMemberId: Long?,
    onSelectMember: (Long) -> Unit,
    category: Category?,
    onCategory: (Category?) -> Unit,
    description: String,
    onDescription: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedLabel = members.firstOrNull { it.id == selectedMemberId }?.toRosterEntry()?.display() ?: "选择名单成员"

    Column {
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLabel)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                members.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.toRosterEntry().display()) },
                        onClick = { onSelectMember(m.id); menuExpanded = false },
                    )
                }
            }
        }

        Row {
            FilterChip(selected = category == Category.REWARD, onClick = { onCategory(Category.REWARD) }, label = { Text("奖") })
            FilterChip(
                selected = category == Category.PENALTY,
                onClick = { onCategory(Category.PENALTY) },
                label = { Text("惩") },
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        OutlinedTextField(
            value = description,
            onValueChange = onDescription,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("事迹") },
        )
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text("确认补充") }
    }
}
