package com.enanke.awardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enanke.awardledger.data.CandidateEventEntity
import com.enanke.awardledger.data.LedgerRepository
import com.enanke.awardledger.data.MemberEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfirmViewModel(private val repo: LedgerRepository) : ViewModel() {

    val candidates: StateFlow<List<CandidateEventEntity>> =
        repo.candidatesForReviewFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<MemberEntity>> =
        repo.membersFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirm(id: Long) = viewModelScope.launch { repo.confirmCandidate(id) }

    fun discard(id: Long) = viewModelScope.launch { repo.discardCandidate(id) }

    fun clarify(
        id: Long,
        memberId: Long?,
        name: String?,
        categoryCode: String?,
        description: String?,
    ) = viewModelScope.launch {
        repo.clarifyCandidate(id, memberId, name, categoryCode, description)
    }
}
