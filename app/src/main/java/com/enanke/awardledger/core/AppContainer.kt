package com.enanke.awardledger.core

import android.content.Context
import com.enanke.awardledger.ai.AiGateway
import com.enanke.awardledger.ai.DeepSeekClient
import com.enanke.awardledger.ai.DemoAiGateway
import com.enanke.awardledger.data.AppDatabase
import com.enanke.awardledger.data.LedgerRepository

/**
 * 手工依赖容器（不引入 DI 框架，保持可审查、构建简单）。
 *
 * gateway 不固定在启动时：每次结构化请求都按最新设置动态创建，
 * 因此在“AI 连接设置”里保存地址/令牌后，新请求会立即使用新配置。
 */
class AppContainer(context: Context) {
    val settings: SettingsStore = SettingsStore(context)
    val connectivity: ConnectivityObserver = ConnectivityObserver(context)
    val database: AppDatabase = AppDatabase.build(context)

    private val httpClient = DeepSeekClient.defaultHttpClient()

    private fun createGateway(): AiGateway =
        if (settings.isDemoMode()) {
            DemoAiGateway()
        } else {
            DeepSeekClient(
                baseUrl = settings.proxyBaseUrl(),
                accessToken = settings.proxyAccessToken(),
                http = httpClient,
            )
        }

    val repository: LedgerRepository = LedgerRepository(database, gatewayProvider = { createGateway() })
}
