package com.enanke.awardledger.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enanke.awardledger.data.AuditLogEntity
import com.enanke.awardledger.data.LedgerRecordEntity
import com.enanke.awardledger.data.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(private val repo: LedgerRepository) : ViewModel() {

    val records: StateFlow<List<LedgerRecordEntity>> =
        repo.currentRecordsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val auditLog: StateFlow<List<AuditLogEntity>> =
        repo.auditLogFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var consistencyIssues by mutableStateOf<List<String>>(emptyList())
        private set

    fun checkConsistency() = viewModelScope.launch {
        consistencyIssues = repo.verifyConsistency()
    }

    fun recover() = viewModelScope.launch { repo.recoverInterruptedWrites() }
}
