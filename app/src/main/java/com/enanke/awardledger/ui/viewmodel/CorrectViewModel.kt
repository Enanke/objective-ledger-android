package com.enanke.awardledger.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enanke.awardledger.data.LedgerRecordEntity
import com.enanke.awardledger.data.LedgerRepository
import com.enanke.awardledger.data.MemberEntity
import com.enanke.awardledger.domain.model.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 更正结果：界面据此决定是否关闭弹窗、展示错误。 */
sealed interface CorrectionResult {
    data object Success : CorrectionResult
    data class Failure(val message: String) : CorrectionResult
}

class CorrectViewModel(private val repo: LedgerRepository) : ViewModel() {

    val records: StateFlow<List<LedgerRecordEntity>> =
        repo.currentRecordsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<MemberEntity>> =
        repo.membersFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _correctionResult = mutableStateOf<CorrectionResult?>(null)
    val correctionResult: State<CorrectionResult?> = _correctionResult

    fun clearResult() {
        _correctionResult.value = null
    }

    /**
     * 应用更正：校验描述非空；结果以 Success/Failure 回传，
     * 由界面在成功时才关闭弹窗、失败时展示错误。
     */
    fun applyCorrection(
        recordId: Long,
        newMemberId: Long,
        category: Category,
        description: String,
    ) = viewModelScope.launch {
        if (description.isBlank()) {
            _correctionResult.value = CorrectionResult.Failure("事迹描述不能为空。")
            return@launch
        }
        val outcome = repo.correctRecord(recordId, newMemberId, category, description)
        _correctionResult.value = when (outcome) {
            is LedgerRepository.CorrectOutcome.Corrected -> CorrectionResult.Success
            is LedgerRepository.CorrectOutcome.AlreadyCorrected -> CorrectionResult.Success
            is LedgerRepository.CorrectOutcome.Error -> CorrectionResult.Failure(outcome.message)
        }
    }
}
