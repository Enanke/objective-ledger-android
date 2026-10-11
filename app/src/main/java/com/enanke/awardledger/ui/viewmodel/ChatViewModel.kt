package com.enanke.awardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enanke.awardledger.core.ConnectivityObserver
import com.enanke.awardledger.data.CandidateEventEntity
import com.enanke.awardledger.data.ChatMessageEntity
import com.enanke.awardledger.data.LedgerRepository
import com.enanke.awardledger.domain.model.ProcessState
import com.enanke.awardledger.domain.model.ReviewStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** 聊天界面条目（带稳定 key，供 LazyColumn 去重）。 */
sealed interface ChatItem {
    val key: String
    data class User(val message: ChatMessageEntity) : ChatItem {
        override val key: String get() = "u${message.id}"
    }
    data class Assistant(val text: String, val kind: AssistantKind, override val key: String) : ChatItem
}

enum class AssistantKind { SUMMARY, QUESTION, ERROR }

class ChatViewModel(
    private val repo: LedgerRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    val online: StateFlow<Boolean> =
        connectivity.online.stateIn(viewModelScope, SharingStarted.Eagerly, connectivity.online.value)

    val chatItems: StateFlow<List<ChatItem>> =
        combine(repo.messagesFlow(), repo.candidatesFlow()) { msgs, cands ->
            buildChatItems(msgs, cands)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /**
     * 发送：先持久化原话，成功后再通过 [onDone] 清空输入；[sending] 防双击重复发送。
     */
    fun send(text: String, onDone: () -> Unit) {
        val t = text.trim()
        if (t.isEmpty() || _sending.value) return
        // 同步置位，防同一帧连击。
        _sending.value = true
        viewModelScope.launch {
            try {
                // 幂等：每条消息带客户端唯一 id，重复发送不会重复入账。
                repo.sendMessage(UUID.randomUUID().toString(), t, System.currentTimeMillis())
                onDone() // 持久化成功即清空输入
            } finally {
                _sending.value = false
            }
            // 解析异步继续；处理异常不影响“已发送”的原话展示。
            runCatching { if (connectivity.online.value) repo.processPending() }
        }
    }

    fun retryPending() = viewModelScope.launch { repo.processPending() }

    fun recover() = viewModelScope.launch { repo.recoverInterruptedWrites() }

    private fun buildChatItems(
        msgs: List<ChatMessageEntity>,
        cands: List<CandidateEventEntity>,
    ): List<ChatItem> {
        val byMsg = cands.groupBy { it.chatMessageId }
        val needs = setOf(
            ReviewStatus.NEEDS_NAME,
            ReviewStatus.NEEDS_CATEGORY,
            ReviewStatus.NEEDS_DESCRIPTION,
        )
        val items = mutableListOf<ChatItem>()
        for (m in msgs.sortedBy { it.sentAtMillis }) {
            items.add(ChatItem.User(m))
            val cs = byMsg[m.id].orEmpty()
            when {
                cs.isNotEmpty() -> {
                    val ready = cs.count { it.status == ReviewStatus.READY_TO_CONFIRM }
                    val need = cs.count { it.status in needs }
                    items.add(
                        ChatItem.Assistant(
                            "已解析为 ${cs.size} 条候选：$ready 条可入账，$need 条待补充。",
                            AssistantKind.SUMMARY,
                            "s${m.id}",
                        ),
                    )
                    cs.filter { it.status in needs && !it.question.isNullOrBlank() }.forEach { c ->
                        items.add(ChatItem.Assistant(c.question!!, AssistantKind.QUESTION, "q${c.id}"))
                    }
                }
                m.state == ProcessState.FAILED -> items.add(
                    ChatItem.Assistant("解析失败，请检查网络或代理配置后重试。", AssistantKind.ERROR, "e${m.id}"),
                )
                else -> Unit
            }
        }
        return items
    }
}
