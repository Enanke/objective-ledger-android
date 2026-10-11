package com.enanke.awardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enanke.awardledger.data.ChatMessageEntity
import com.enanke.awardledger.data.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PendingViewModel(private val repo: LedgerRepository) : ViewModel() {

    val pending: StateFlow<List<ChatMessageEntity>> =
        repo.pendingMessagesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() = viewModelScope.launch { repo.processPending() }
}
