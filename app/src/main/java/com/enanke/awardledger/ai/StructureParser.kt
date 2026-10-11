package com.enanke.awardledger.ai

import com.enanke.awardledger.domain.model.RawCandidate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** AI 返回的原始结构（宽松解析，字段缺失使用默认值）。 */
@Serializable
data class AiStructuredResponse(
    val candidates: List<AiCandidateDto> = emptyList(),
)

@Serializable
data class AiCandidateDto(
    val name: String = "",
    @SerialName("nameExplicit") val nameExplicit: Boolean = true,
    val category: String = "",
    val description: String = "",
    val question: String = "",
)

/**
 * 结构解析器：把模型输出稳健地解析为 [RawCandidate]。
 * 处理 markdown 代码块包裹、纯 JSON、以及缺失字段等常见情况；失败返回空列表（由上层判定失败并重试）。
 */
object StructureParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseCandidates(content: String?): List<RawCandidate> {
        if (content.isNullOrBlank()) return emptyList()
        val extracted = extractJson(content) ?: return emptyList()
        return try {
            val resp = json.decodeFromString<AiStructuredResponse>(extracted)
            resp.candidates.map { it.toRaw() }
        } catch (e: Exception) {
            // 兜底：尝试把顶层 JSON 对象当作 candidates 数组
            parseCandidatesArray(extracted)
        }
    }

    private fun parseCandidatesArray(extracted: String): List<RawCandidate> {
        return try {
            val el = json.parseToJsonElement(extracted)
            val arr = when (el) {
                is kotlinx.serialization.json.JsonArray -> el
                is kotlinx.serialization.json.JsonObject ->
                    el["candidates"] as? kotlinx.serialization.json.JsonArray
                else -> null
            } ?: return emptyList()
            arr.mapNotNull { elem ->
                val obj = elem.jsonObject
                RawCandidate(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    nameExplicit = obj["nameExplicit"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                    categoryCode = obj["category"]?.jsonPrimitive?.content ?: "",
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    question = obj["question"]?.jsonPrimitive?.content ?: "",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun AiCandidateDto.toRaw() = RawCandidate(
        name = name,
        nameExplicit = nameExplicit,
        categoryCode = category,
        description = description,
        question = question,
    )

    /** 从可能的 markdown 代码块中提取 JSON 文本。 */
    fun extractJson(content: String): String? {
        val trimmed = content.trim()
        // 去掉 ```json ... ``` 围栏
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(trimmed)
        if (fenced != null) return fenced.groupValues[1].trim()

        // 找到第一个 { 到最后一个 } 的子串
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return null
    }
}
