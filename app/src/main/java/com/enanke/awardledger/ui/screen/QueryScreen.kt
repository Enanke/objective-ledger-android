package com.enanke.awardledger.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enanke.awardledger.core.AppContainer
import com.enanke.awardledger.ui.viewmodel.QueryViewModel

@Composable
fun QueryScreen(container: AppContainer) {
    val vm: QueryViewModel = viewModel { QueryViewModel(container.repository) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("按姓名查询台账", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vm.name,
            onValueChange = { vm.name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("姓名（名单内完整姓名）") },
        )
        Button(onClick = { vm.resolveName() }, modifier = Modifier.padding(top = 8.dp)) { Text("查询") }

        if (vm.matchingMembers.isNotEmpty()) {
            Text("名单中有同名成员，请选择具体是哪一位：", Modifier.padding(top = 8.dp))
            vm.matchingMembers.forEach { m ->
                OutlinedButton(
                    onClick = { vm.queryMember(m.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text(m.toRosterEntry().display()) }
            }
        }

        Text(
            vm.result,
            Modifier.fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
