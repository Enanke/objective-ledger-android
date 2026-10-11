# 架构说明

## 分层

```
ui/ (Compose + ViewModel)
   │  状态流 / 用户动作
   ▼
data/LedgerRepository  ← 业务唯一入口（事务、幂等、审计）
   │
   ├── Room (AppDatabase + DAO)      # 持久化：名单/消息/候选/台账/审计
   └── ai/AiGateway                  # 只做“结构化”，不写库
          ├── DeepSeekClient（真实，经代理）
          └── DemoAiGateway（演示/测试，确定性）

domain/（纯 Kotlin，无 Android 依赖，可 JVM 单测）
   NameMatcher / StructureValidator / QueryNumbering / RosterImport
```

## 数据流

### 1. 聊天录入 → 结构化

1. 班干部发送中文原话；`sendMessage(clientMessageId, rawText, sentAt)` 先持久化原话与时间，状态 `PENDING`（离线也可显示“待处理”）。
2. 在线时 `processMessage`：调用 AI 把原话结构化为 `List<RawCandidate>`。
3. `StructureValidator` 逐项本地核对（姓名唯一匹配、类别、事迹），产出 `ReviewStatus`：
   - `READY_TO_CONFIRM`：姓名唯一 + 字段齐全 → 进“待确认”。
   - `NEEDS_NAME/CATEGORY/DESCRIPTION`：进“待确认”并追问，**绝不猜测**。
4. 候选以 `idempotencyKey = clientMessageId#index` 去重持久化，消息状态 `PARSED`。

### 2. 入账（确认）

`confirmCandidate` 在一个事务里：写台账记录 + 标记候选 `CONFIRMED` + 写审计日志。
- 台账记录携带 `member_id`（内部 id）+ `member_name`，同名不混淆。
- 幂等：`idempotency_key` 唯一，重复确认直接返回既有记录。

### 3. 更正

`correctRecord` 在一个事务里：保留原记录并置 `SUPERSEDED` → 插入新 `CURRENT` 记录（`supersedes_id` 指向原记录，`revision+1`）→ 写审计日志（before/after 快照）。
- 支持把记录归属更正为名单中另一成员（改 `member_id`）。
- 校验描述非空。
- 幂等：已更正过的记录返回其当前记录，不重复更正。

### 4. 查询

- 输入姓名 → `findMembersByName` 解析成员：
  - 0 人：提示名单中不存在。
  - 1 人：直接查询。
  - 多人（同名）：要求用户先选定内部成员 id，**绝不混合同名者**。
- `summarizeRecords(memberId)` 只返回数据库生成的确定性编号正文，每行含真实记录 ID、姓名、类别、描述。**不经过 AI**。

### 5. 恢复与核查

- `recoverInterruptedWrites`：把遗留 `PROCESSING` 重置为 `PENDING`。
- `verifyConsistency`：交叉检查“已确认候选有台账”“当前台账有审计日志”“无遗留 PROCESSING”。

### 6. 名册（用户自行维护，升级安全）

成员表带 `active` 标记；**所有可选名单查询只取 `active = 1`**，因此“可选活动名单”始终是可精确控制的集合。

- **仓库不含任何内置姓名**：新安装为空名册，全部由用户在「名册」页录入；名册只保存在本机 Room 数据库。
- `ensureInitialized()` **不写入、也不重置任何名单**；已有数据库的现有成员一律不触碰（保留 `active`、`id` 与全部历史）。
- 名册操作（`LedgerRepository`，每个变更都写 `audit_log`，`entity_type = MEMBER`，before/after 为成员快照 JSON）：
  - `importMembers(rawText)`：批量导入/补充。按行（兼容逗号/顿号等）拆分、去首尾空白、**忽略空行**；与**活动**成员同名者跳过 → **重复导入幂等**；整批写入同一事务。
  - `addMember(name, note)` / `renameMember(id, name, note)`：**同名必须显式消歧** —— 若活动成员中已存在同名者，必须提供与既有者都不同的“区分信息”，否则**明确拒绝并说明**（绝不猜测、绝不静默合并）；改名**保持 member id 不变**。
  - `setMemberActive(id, active)`：**停用替代删除** —— 只改 `active` 标记，成员行与 id 保留。
- **台账历史不受名册变更影响**：台账记录自带 `member_name` 姓名快照；改名/停用**不会**回写或重关联既有记录（`member_id`/`member_name` 原样保留）。
- `MIGRATION_1_2` **只做 schema 变更**（移除 `occurred_at`）：**不删除任何行、不调用 `clearAllTables()`**；成员、消息、候选、台账记录、审计日志全部原样保留。
- 成员匹配只用姓名；内部自增 `id` 仅用于关联与幂等，**学号/序号不参与任何映射**。

## 幂等与原子性

- 消息幂等：`chat_messages.client_message_id` 唯一。
- 候选幂等：`candidate_events.idempotency_key` 唯一。
- 台账幂等：`ledger_records.idempotency_key` 唯一。
- 事务：所有“写台账 + 写审计”都在 `withTransaction` 内原子提交。

## 为什么查询不交给 AI

模型即使被要求“只写引言、保留编号”，仍可能在引言里编造事实或张冠李戴。因此查询结果**完全由数据库确定性生成**，从机制上保证“逐条对应真实记录且附编号，模型无法添加或遗漏记录”。
