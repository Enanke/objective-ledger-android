package com.enanke.awardledger.ai

import com.enanke.awardledger.domain.model.RawCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DeepSeek 客户端（通过云端代理转发）。
 *
 * 安全模型：
 * - 本类不持有 DeepSeek API key，请求体也不含任何密钥。
 * - [accessToken] 是代理的访问令牌，由使用者运行时输入（不硬编码进 APK/仓库），
 *   通过 `Authorization: Bearer <token>` 发送给代理；代理校验后自行注入 DeepSeek key。
 * - baseUrl 指向代理根地址，拼成 `<baseUrl>/v1/chat/completions`。
 */
class DeepSeekClient(
    private val baseUrl: String,
    private val accessToken: String = "",
    private val http: OkHttpClient = defaultHttpClient(),
) : AiGateway {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val completionsUrl = "${baseUrl.trimEnd('/')}/v1/chat/completions"

    override suspend fun structure(rawText: String, roster: List<String>): List<RawCandidate> {
        val system = PromptBuilder.structureSystemPrompt(roster)
        val user = PromptBuilder.structureUserPrompt(rawText)
        val payload = buildJsonObject {
            put("model", "deepseek-chat")
            put("temperature", 0)
            put("stream", false)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            }
            putJsonObject("response_format") { put("type", "json_object") }
        }.toString()
        val content = call(payload)
        return StructureParser.parseCandidates(content)
    }

    private suspend fun call(payload: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(completionsUrl)
            .post(payload.toRequestBody(mediaType))
            .header("Accept", "application/json")
        if (accessToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AiCallException("HTTP ${resp.code}: ${body.take(400)}")
            }
            extractContent(body)
        }
    }

    private fun extractContent(body: String): String {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val choices = root["choices"]?.jsonArray
            val first = choices?.firstOrNull()?.jsonObject
            first?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()
        } catch (e: Exception) {
            throw AiCallException("无法解析模型响应: ${body.take(400)}")
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

/** AI 调用失败（网络/HTTP/解析），由上层决定重试或标记 FAILED。 */
class AiCallException(message: String) : Exception(message)
