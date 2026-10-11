/**
 * 奖惩台账云端代理（Cloudflare Worker）。
 *
 * 作用：把 DeepSeek API key 保存在服务端 Secret（DEEPSEEK_API_KEY），
 * 客户端只请求本 Worker，请求/响应中都不含 DeepSeek key。
 *
 * 安全（重要，勿部署成开放代理）：
 * 1. 鉴权：Worker Secret 配置随机访问令牌 ACCESS_TOKEN；客户端每次请求带
 *    `Authorization: Bearer <ACCESS_TOKEN>`，Worker 逐一校验，不匹配返回 401。
 * 2. 仅 HTTPS：除本机回环地址（本地开发）外，一律拒绝明文 HTTP。
 * 3. 限制请求体大小（默认 64KB）。按 IP 的频率限制为“单实例内存态”，
 *    不构成全局限流保证；生产务必再用 Cloudflare WAF Rate Limiting 规则做限流。
 * 4. 只允许 POST /v1/chat/completions，且只转发到固定上游。
 *
 * 部署：
 *   1. `wrangler secret put DEEPSEEK_API_KEY` 填入 DeepSeek key；
 *   2. `wrangler secret put ACCESS_TOKEN` 填入一个随机长令牌（如 `openssl rand -hex 32`）；
 *   3. `wrangler deploy`，把返回域名与 ACCESS_TOKEN 分别填入应用的代理地址与访问令牌。
 */

const UPSTREAM = 'https://api.deepseek.com';
const MAX_BODY_BYTES = 64 * 1024; // 64KB
const RATE_LIMIT = 30; // 每 IP 每分钟
const RATE_WINDOW_MS = 60 * 1000;

// 简易固定窗口限流：仅单实例内存态，不保证全局限流（多实例/冷启动不共享计数）。
// 真正的限流应依赖 Cloudflare WAF Rate Limiting 规则。
const hits = new Map();

function isLocalHost(hostname) {
  return hostname === '127.0.0.1' || hostname === 'localhost' || hostname === '10.0.2.2';
}

function json(obj, status, extraHeaders) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: Object.assign({ 'Content-Type': 'application/json' }, extraHeaders || {}),
  });
}

function rateLimitExceeded(ip) {
  const now = Date.now();
  const rec = hits.get(ip);
  if (!rec || now - rec.start >= RATE_WINDOW_MS) {
    hits.set(ip, { start: now, count: 1 });
    return false;
  }
  rec.count += 1;
  return rec.count > RATE_LIMIT;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 仅 HTTPS（本机回环用于本地联调除外）。
    if (url.protocol !== 'https:' && !isLocalHost(url.hostname)) {
      return json({ error: { message: 'https required' } }, 400);
    }

    // 鉴权。
    const token = env.ACCESS_TOKEN;
    if (!token) {
      return json({ error: { message: 'proxy not configured (missing ACCESS_TOKEN)' } }, 500);
    }
    const auth = request.headers.get('Authorization') || '';
    const expected = 'Bearer ' + token;
    // 常量时间比较，降低时序侧信道风险。
    if (auth.length !== expected.length || !timingSafeEqual(auth, expected)) {
      return json({ error: { message: 'unauthorized' } }, 401);
    }

    // 方法/路径白名单。
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    if (request.method !== 'POST') {
      return json({ error: { message: 'method not allowed' } }, 405);
    }
    const path = url.pathname.replace(/^\/+/, '');
    if (path !== 'v1/chat/completions' && path !== 'chat/completions') {
      return json({ error: { message: 'not found' } }, 404);
    }

    // 限流。
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    if (rateLimitExceeded(ip)) {
      return json({ error: { message: 'rate limit exceeded' } }, 429);
    }

    // 请求体大小限制。
    const contentLength = Number(request.headers.get('Content-Length') || 0);
    if (contentLength > MAX_BODY_BYTES) {
      return json({ error: { message: 'payload too large' } }, 413);
    }

    const apiKey = env.DEEPSEEK_API_KEY;
    if (!apiKey) {
      return json({ error: { message: 'proxy not configured (missing DEEPSEEK_API_KEY)' } }, 500);
    }

    let body;
    try {
      body = await request.text();
    } catch (e) {
      return json({ error: { message: 'bad request' } }, 400);
    }
    // 注意：Workers 默认无 Node Buffer，用 TextEncoder 计算字节数。
    if (new TextEncoder().encode(body).length > MAX_BODY_BYTES) {
      return json({ error: { message: 'payload too large' } }, 413);
    }

    const upstream = new Request(UPSTREAM + '/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + apiKey,
        'Accept': 'application/json',
      },
      body: body,
    });

    const resp = await fetch(upstream);
    const text = await resp.text();
    return new Response(text, {
      status: resp.status,
      headers: Object.assign(
        { 'Content-Type': 'application/json' },
        corsHeaders(),
      ),
    });
  },
};

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Max-Age': '86400',
  };
}

function timingSafeEqual(a, b) {
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}
