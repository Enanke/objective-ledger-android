package com.enanke.awardledger.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.enanke.awardledger.core.AppContainer
import com.enanke.awardledger.domain.ProxyConfigValidator

/**
 * “AI 连接设置”对话框：
 * - 显示当前状态（演示模式 / 已配置 DeepSeek 代理）。
 * - 输入代理地址与访问令牌并保存；保存后新请求动态使用新配置。
 * - 令牌不回显：字段每次打开都是空的，仅提示“已保存/未保存”，保存时仅在用户新输入时覆盖。
 * - 地址只接受 HTTPS 或本机开发回环（10.0.2.2/127.0.0.1/localhost）。
 */
@Composable
fun AiSettingsDialog(container: AppContainer, onDismiss: () -> Unit) {
    val settings = container.settings
    var baseUrl by remember { mutableStateOf(settings.proxyBaseUrl()) }
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val hasSavedToken = settings.proxyAccessToken().isNotBlank()
    val modeLabel = if (settings.proxyBaseUrl().isBlank()) {
        "演示模式（离线确定性解析，不联网）"
    } else {
        "已配置 DeepSeek 代理"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 连接设置") },
        text = {
            Column {
                Text("当前状态：$modeLabel")
                Text(
                    if (hasSavedToken) "已保存访问令牌（出于安全，此处不回显）。"
                    else "尚未保存访问令牌。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("代理根地址") },
                    placeholder = { Text("https://your-proxy.example.com（根地址，留空=演示模式）") },
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasSavedToken) "访问令牌（留空则保持不变）" else "访问令牌") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                message?.let {
                    Text(
                        it,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = baseUrl.trim()
                when {
                    !ProxyConfigValidator.isValidBaseUrl(url) -> {
                        message = "地址无效：仅接受代理根地址（HTTPS 或本机回环，不含 /v1/... 路径、query、fragment）。"
                        isError = true
                    }
                    url.isNotBlank() && !hasSavedToken && token.isBlank() -> {
                        message = "请填写访问令牌。"
                        isError = true
                    }
                    else -> {
                        settings.setProxyBaseUrl(url)
                        if (token.isNotBlank()) settings.setProxyAccessToken(token)
                        message = if (url.isBlank()) "已切换为演示模式。" else "已保存，新请求将使用新配置。"
                        isError = false
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
