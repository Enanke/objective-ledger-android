package com.enanke.awardledger.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enanke.awardledger.data.LedgerRepository
import com.enanke.awardledger.data.MemberEntity
import kotlinx.coroutines.launch

class QueryViewModel(private val repo: LedgerRepository) : ViewModel() {

    var name by mutableStateOf("")
    var result by mutableStateOf("")
    var matchingMembers by mutableStateOf<List<MemberEntity>>(emptyList())

    /** 先按姓名解析；同名时要求用户选定内部成员 id，绝不混合同名者。 */
    fun resolveName() = viewModelScope.launch {
        val n = name.trim()
        if (n.isEmpty()) {
            result = "请输入要查询的姓名。"
            matchingMembers = emptyList()
            return@launch
        }
        val matches = repo.findMembersByName(n)
        when {
            matches.isEmpty() -> {
                matchingMembers = emptyList()
                result = "名单中没有“$n”。"
            }
            matches.size == 1 -> {
                matchingMembers = emptyList()
                result = repo.summarizeRecords(matches[0].id)
            }
            else -> {
                matchingMembers = matches
                result = "名单中有 ${matches.size} 位“$n”，请选择具体是哪一位。"
            }
        }
    }

    fun queryMember(id: Long) = viewModelScope.launch {
        result = repo.summarizeRecords(id)
    }
}
