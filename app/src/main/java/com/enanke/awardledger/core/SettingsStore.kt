package com.enanke.awardledger.core

import android.content.Context
import android.content.SharedPreferences
import com.enanke.awardledger.BuildConfig

/**
 * 极简设置存储（SharedPreferences）。
 * 云端代理地址运行时可配；绝不存储 DeepSeek API key（key 只存在于代理服务端）。
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("award_ledger_settings", Context.MODE_PRIVATE)

    fun proxyBaseUrl(): String =
        prefs.getString(KEY_PROXY_URL, BuildConfig.AI_PROXY_BASE_URL)?.trim().orEmpty()

    fun setProxyBaseUrl(url: String) {
        prefs.edit().putString(KEY_PROXY_URL, url.trim()).apply()
    }

    /** 代理访问令牌（运行时输入，绝不硬编码进 APK/仓库）。 */
    fun proxyAccessToken(): String = prefs.getString(KEY_PROXY_TOKEN, "")?.orEmpty() ?: ""

    fun setProxyAccessToken(token: String) {
        prefs.edit().putString(KEY_PROXY_TOKEN, token.trim()).apply()
    }

    fun isDemoMode(): Boolean = proxyBaseUrl().isBlank()

    companion object {
        private const val KEY_PROXY_URL = "proxy_base_url"
        private const val KEY_PROXY_TOKEN = "proxy_access_token"
    }
}
