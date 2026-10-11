package com.enanke.awardledger.ai

import com.enanke.awardledger.domain.model.RawCandidate

/**
 * 演示/离线网关：不联网，用确定性规则做结构化。
 * 仅用于开发联调与单元测试（模拟 AI），真实解析走 DeepSeekClient。
 * 规则保守：只识别名单中精确出现的姓名，绝不猜测；识别不到则置空触发追问。
 */
class DemoAiGateway : AiGateway {

    override suspend fun structure(rawText: String, roster: List<String>): List<RawCandidate> {
        val names = roster.filter { rawText.contains(it) }
        if (names.isEmpty()) {
            return listOf(
                RawCandidate(
                    name = "",
                    nameExplicit = false,
                    categoryCode = inferCategory(rawText),
                    description = rawText,
                    question = "请问这件事是哪个同学？请给出名单里的完整姓名。",
                ),
            )
        }
        return names.map { n ->
            RawCandidate(
                name = n,
                nameExplicit = true,
                categoryCode = inferCategory(rawText),
                description = rawText,
                question = "",
            )
        }
    }

    private fun inferCategory(text: String): String = when {
        Regex("表扬|奖励|加分|表彰|奖|进步|优秀|做好事|助人|认真").containsMatchIn(text) -> "REWARD"
        Regex("批评|惩罚|扣分|处分|违纪|迟到|打架|骂人|违规|没交").containsMatchIn(text) -> "PENALTY"
        else -> ""
    }
}
