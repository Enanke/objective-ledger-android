package com.enanke.awardledger.domain

import java.net.URI

/**
 * 代理根地址校验。
 *
 * 客户端会在根地址后自行追加 `/v1/chat/completions`，因此这里只接受“代理根地址”：
 * - scheme + host + 可选端口，允许尾部一个 `/`；
 * - 拒绝任何非根路径（如 `/v1/chat/completions`）、query、fragment、userinfo；
 * - 只允许 HTTPS，或明确的本机开发回环（明文 HTTP 仅限这些主机）；
 * - 空地址表示演示模式（合法，不联网）。
 */
object ProxyConfigValidator {

    private val loopbackHosts = setOf("10.0.2.2", "127.0.0.1", "localhost")

    fun isValidBaseUrl(url: String): Boolean {
        val t = url.trim()
        if (t.isEmpty()) return true // 空 = 演示模式
        return try {
            val u = URI(t)
            val scheme = u.scheme?.lowercase()
            val host = u.host?.lowercase()
            val path = u.rawPath.orEmpty()
            val isRoot = path.isEmpty() || path == "/"
            val noExtras = u.rawQuery == null && u.rawFragment == null && u.rawUserInfo == null
            when {
                !noExtras || !isRoot -> false
                scheme == "https" && !host.isNullOrBlank() -> true
                scheme == "http" && host in loopbackHosts -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}
