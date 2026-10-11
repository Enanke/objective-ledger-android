package com.enanke.awardledger.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enanke.awardledger.data.LedgerRepository
import com.enanke.awardledger.data.MemberEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RosterViewModel(private val repo: LedgerRepository) : ViewModel() {

    /** 含已停用成员；启用在前。 */
    val members: StateFlow<List<MemberEntity>> =
        repo.allMembersFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var importText by mutableStateOf("")
    var newName by mutableStateOf("")
    var newNote by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    fun import() = viewModelScope.launch {
        if (importText.isBlank()) {
            message = "请先粘贴姓名（每行一个，空行会被忽略）。"
            return@launch
        }
        val report = repo.importMembers(importText)
        message = report.summary
        importText = ""
    }

    fun add() = viewModelScope.launch {
        when (val r = repo.addMember(newName, newNote)) {
            is LedgerRepository.RosterResult.Ok -> {
                message = "已新增「${r.member.display()}」。"
                newName = ""
                newNote = ""
            }
            is LedgerRepository.RosterResult.Rejected -> message = r.message
        }
    }

    fun rename(id: Long, name: String, note: String?) = viewModelScope.launch {
        when (val r = repo.renameMember(id, name, note)) {
            is LedgerRepository.RosterResult.Ok ->
                message = "已改名为「${r.member.display()}」；member id 不变，台账历史姓名快照不变。"
            is LedgerRepository.RosterResult.Rejected -> message = r.message
        }
    }

    fun setActive(id: Long, active: Boolean) = viewModelScope.launch {
        when (val r = repo.setMemberActive(id, active)) {
            is LedgerRepository.RosterResult.Ok ->
                message = if (active) {
                    "已启用「${r.member.display()}」。"
                } else {
                    "已停用「${r.member.display()}」；成员行与 id 保留、未删除，台账历史不受影响。"
                }
            is LedgerRepository.RosterResult.Rejected -> message = r.message
        }
    }

    fun clearMessage() {
        message = null
    }

    private fun MemberEntity.display(): String = note?.let { "$name（$it）" } ?: name
}
