# 测试计划

## 测试分层

- **纯 JVM 单测**（`app/src/test`）：`domain` 与 `ai` 的纯逻辑，无 Android 依赖。
- **Robolectric 集成测试**（`app/src/test`）：内存 Room 数据库上跑完整业务流程；迁移测试**自建 v1 SQLite 库后直接调用 `MIGRATION_1_2`**（不依赖 assets/schema JSON，规避 `MigrationTestHelper` 在单元测试里读不到 assets 的问题）。
- **instrumented 测试**（`app/src/androidTest`，预留）：需真机/模拟器。

## 需求 → 用例映射

| 需求 | 用例 |
|---|---|
| 单人录入 | `LedgerRepositoryTest.single entry full flow` |
| 多人同句 | `multi person sentence splits`、`StructureValidatorTest.multi person` |
| 姓名不明确 | `unknown name stays needs name`、`NameMatcherTest.*` |
| 断网恢复 | `recover resets stale processing`；UI 层联网后自动 `processPending` |
| 重复发送幂等 | `duplicate send with same client id`、`reprocessing does not duplicate`、`confirm is idempotent` |
| 写入中断恢复 | `recover resets stale processing`、`verifyConsistency` |
| 更正保留原记录+审计 | `correction preserves original and writes audit` |
| 更正改归属 | `correction can reassign record to another member` |
| 查询与台账逐条一致 | `query result is deterministic and contains record id and name` |
| 同名查询先选成员 | `same name query requires member id and never mixes` |
| 新装空名册、初始化不种子化 | `LedgerRepositoryTest.new database has an empty roster and init never seeds`、`init never touches existing members` |
| 批量导入幂等 / 去空白 / 忽略空行 | `RosterImportTest.parseNames *`、`countBlankItems *`、`LedgerRepositoryTest.batch import trims ignores blanks and is idempotent` |
| 空姓名拒绝 | `RosterImportTest.normalizeName trims blanks and rejects empty`、`LedgerRepositoryTest.blank name is rejected` |
| 重名必须显式消歧、绝不猜人 | `LedgerRepositoryTest.duplicate name must be disambiguated explicitly never guessed`、`NameMatcherTest.*`、`same name query requires member id...` |
| 改名/停用不影响既有台账与审计 | `LedgerRepositoryTest.rename keeps member id and ledger name snapshot and writes audit`、`deactivate replaces delete and preserves row ledger and audit` |
| 升级保留现有成员与全部数据 | `AppDatabaseMigrationTest.migration 1 to 2 preserves all data and only drops occurred_at`、`room migrates a v1 database and validates the schema` |

## 缺陷回归（多轮审查）

| 缺陷 / 变更 | 用例/修复 |
|---|---|
| AI 引言可能编造 → 查询改为纯确定性 | `query result is deterministic...`（不再调用 AI） |
| 同名永远消不了歧 | `same name query requires member id...`（内部 id 选定） |
| 审计 JSON 转义 | `audit json escaping round trips special characters` |
| 更正描述为空 | `correction rejects blank description` |
| 同名混合查询 | 已移除按姓名直接查台账的 DAO，改为按 member_id |
| 更正弹窗失败即关闭 | `CorrectViewModel` 显式 Success/Failure，界面仅在 Success 关闭并展示错误 |
| 初始化并发 | `AwardLedgerRoot` 顺序 init → recover → 订阅联网 |
| 发送同帧连击/未发送假象 | `ChatViewModel` 同步置位 + 持久化成功即清空 + 解析异步 |
| Worker 依赖 Node Buffer | `worker.js` 改用 `TextEncoder().encode(body).length`；限流注明仅单实例 |
| 配置方法无调用、gateway 启动即固定 | `AppContainer` 改为每次请求动态创建 gateway；新增 `AiSettingsDialog` |
| 代理地址校验（HTTPS/回环根地址） | `ProxyConfigValidatorTest` |
| 设置持久化与演示模式判断 | `SettingsStoreTest`（Robolectric） |
| **内置名单残留（开源风险）** | 移除全部内置姓名：`domain/Roster.kt` 只保留纯文本导入 `RosterImport`；新装为空名册，名册只存本机 Room |
| **事迹发生时间必填/追问** | 移除 `NEEDS_TIME`、`TimeParser`、`occurred_at` 列与全部表单/展示；不再因缺少时间追问或阻止入账 |
| **Room 迁移** | `MIGRATION_1_2` **只**移除 `occurred_at` 列；不删数据、不调用 `clearAllTables()`，记录/消息/审计全保留 |
| **旧成员隔离逻辑** | 取消 `ensureSeeded()` 的停用/补齐；`ensureInitialized()` 不写入不重置，既有成员一律不触碰 |
| **名册管理缺失** | 新增「名册」页 + `importMembers`/`addMember`/`renameMember`/`setMemberActive`；重名必须显式区分信息，**停用替代删除** |
| **应用显示名称** | `app_name` = 客观存在 |

## 运行

```bash
./gradlew testDebugUnitTest       # JVM + Robolectric（不产出 APK）
./gradlew connectedDebugAndroidTest  # 需设备（预留）
```

> 实际执行情况与受阻点见 `docs/BUILD_REPORT.md`。
