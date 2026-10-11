# 云端代理与 API key 配置

DeepSeek API key **绝不能硬编码进 APK 或仓库**。本项目用一个极简云端代理（Cloudflare Worker）转发请求，key 只保存在服务端 Secret 中。

## 代理做了什么

`proxy/worker.js`：
1. **鉴权**：校验客户端发送的 `Authorization: Bearer <ACCESS_TOKEN>`（常量时间比较），不匹配返回 401。
2. **仅 HTTPS**：除本机回环地址外一律拒绝明文 HTTP。
3. **限制**：请求体 ≤ 64KB，按 IP 每 IP 每分钟 30 次（生产建议叠加 Cloudflare WAF Rate Limiting）。
4. **只转发** `POST /v1/chat/completions` 到固定上游 `https://api.deepseek.com`，并注入服务端的 `DEEPSEEK_API_KEY`。
5. **不开放代理**：需要 ACCESS_TOKEN 才能调用。

## 部署（Cloudflare Workers）

```bash
cd proxy
cp wrangler.toml.example wrangler.toml

# 生成一个随机访问令牌（客户端运行时输入）
openssl rand -hex 32     # 记下这串作为 ACCESS_TOKEN

wrangler secret put DEEPSEEK_API_KEY    # 粘贴你的 DeepSeek key
wrangler secret put ACCESS_TOKEN        # 粘贴上面生成的随机令牌

wrangler deploy
```

部署后会得到形如 `https://award-ledger-proxy.<subdomain>.workers.dev` 的地址。

## 应用端配置（运行时输入，不硬编码）

在应用里：**聊天页顶部「连接设置」** → 填入两项并保存，应用将其存进 SharedPreferences：

| 配置项 | 值 |
|---|---|
| 代理**根地址** `proxy_base_url` | `https://award-ledger-proxy.<subdomain>.workers.dev` |
| 访问令牌 `proxy_access_token` | 上面生成的 `ACCESS_TOKEN` |

- 代理**根地址** = scheme + host + 可选端口（可带尾部 `/`）。**不要**带 `/v1/chat/completions` 等路径——客户端会自动追加该路径，填了会造成重复路径。
- 代理地址留空 → 应用进入**演示模式**（本地确定性解析，不联网）。
- 地址非空 → 应用使用 `DeepSeekClient`，请求带 `Authorization: Bearer <token>` 头。
- 令牌**不回显**：界面只提示“已保存/未保存”，保存时仅在用户新输入时覆盖。
- 地址只接受 HTTPS 根地址，或本机开发回环（`10.0.2.2`/`127.0.0.1`/`localhost`）；拒绝非根路径、query、fragment、userinfo。
- **动态生效**：每次结构化请求按最新设置创建网关，保存后新请求立即使用新配置，无需重启。

代码位置：`ui/screen/AiSettingsDialog.kt`（界面）、`core/SettingsStore.kt`（存储）、`core/AppContainer.kt`（动态装配）、`domain/ProxyConfigValidator.kt`（地址校验）、`ai/DeepSeekClient.kt`（发送）。

## 本地联调（可选）

`app/src/main/res/xml/network_security_config.xml` 仅对本机回环地址（`10.0.2.2`/`127.0.0.1`/`localhost`）放行明文 HTTP，方便本地起一个等价反向代理调试；生产代理必须是 HTTPS。

> 切勿把 `wrangler.toml`、`.dev.vars` 或任何含 key/令牌的文件提交进仓库。本仓库只提供 `wrangler.toml.example` 与 `worker.js`。
