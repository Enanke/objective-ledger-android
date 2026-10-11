package com.enanke.awardledger.ai

import com.enanke.awardledger.domain.model.RawCandidate

/**
 * AI 网关抽象。只做一件事：
 * [structure] 把中文原话结构化为候选事迹（仅解析/措辞，不写台账）。
 *
 * 查询不经过 AI：查询结果由数据库确定性生成（含真实记录 ID 与姓名），
 * 从根本上杜绝模型编造或张冠李戴。真实实现通过云端代理调用 DeepSeek。
 */
interface AiGateway {
    suspend fun structure(rawText: String, roster: List<String>): List<RawCandidate>
}
