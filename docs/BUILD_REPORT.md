# 构建与测试报告（如实记录）

## 结论（当前代码状态，实际运行结果）

- **`testDebugUnitTest`：通过**（**MIT LICENSE + 原创矢量图标这一轮改动之后**）。由用户在本机使用已下载的 **Gradle 8.11.1** 执行：
  ```powershell
  & "$env:TEMP\gradle-8.11.1\bin\gradle.bat" testDebugUnitTest --console=plain
  ```
  输出 `BUILD SUCCESSFUL`；Gradle XML 汇总 **58 tests、0 failures**（含两个 `AppDatabaseMigrationTest` 用例、名册管理用例与名册导入用例）。
- **`assembleDebug`：通过**（同一轮改动之后；由用户在本机执行，**Harness 未运行任何命令**）。
  - 实际命令：`& "$env:TEMP\gradle-8.11.1\bin\gradle.bat" assembleDebug --console=plain`
  - 输出：`BUILD SUCCESSFUL`
  - **产物（debug APK，路径相对工程根目录）**：`app/build/outputs/apk/debug/app-debug.apk`
  - 文件大小：**59,678,085 bytes**；修改时间：**2026-10-11 16:38:23**（已确认实际存在；SHA-256 按要求未记录）
  - 此前几版产物（59,579,609 / 59,820,527 / 59,870,995 bytes）均已被本次产物取代。
- **APK 最终核验**（用户用 `aapt dump badging` 实际执行）：
  - 包名：`com.enanke.awardledger`
  - compileSdk / targetSdk：**35**
  - application-label：**客观存在**
  - 图标资源：`mipmap-anydpi-v26/ic_launcher.xml`（自适应图标）
- **Harness 侧命令仍不可用**（与实际运行无关）：`pwsh` 在工作区被 Windows 文件权限拦下，命令在沙箱初始化写权限阶段即失败——
  ```
  Error: SetNamedSecurityInfoW failed (Win32 5): grantWrite(<工作区根目录>)
  ```
  （原文为工作区根目录的绝对路径，此处已按“文档不含本机绝对路径”的要求泛化。）
  按要求**未切换访问模式、未扩大权限**。因此本报告中"通过"的结论来自**本机 Gradle 的实际执行**，不是 Harness 内的运行。
- **应用显示名称**：客观存在。
- **启动器图标**：**原创矢量自适应图标**（`drawable/ic_launcher_foreground.xml` + `values/colors.xml` + 两个 `mipmap-anydpi-v26` XML；旧位图 PNG 已不再被引用）。

## 测试套件（合计 58 项，0 失败）

| 测试类 | 覆盖 |
|---|---|
| `LedgerRepositoryTest`（Robolectric + 内存 Room） | 录入/多人同句/姓名不明确/幂等/更正/审计转义/恢复/查询/同名消歧/一致性核查/名册管理（空名册·导入幂等·重名消歧·改名停用） |
| `AppDatabaseMigrationTest`（Robolectric） | v1→v2 迁移保留性 + Room 亲自迁移并校验 schema |
| `StructureValidatorTest` / `NameMatcherTest` | 姓名唯一匹配、同名、名单外、字段缺失 |
| `QueryNumberingTest` | 查询正文逐条编号（含真实记录 ID 与姓名） |
| `StructureParserTest` | 模型 JSON 解析与容错 |
| `ProxyConfigValidatorTest` | 代理根地址校验（HTTPS / 本机回环；拒绝非根路径、query、fragment、userinfo） |
| `SettingsStoreTest`（Robolectric） | 代理地址/令牌持久化与演示模式判断 |
| `RosterImportTest` | 批量导入文本解析：去首尾空白、忽略空行、去重、空姓名拒绝 |

## 本轮修复过程（两处，均已由本机重跑验证通过）

在达到上述结果前，`AppDatabaseMigrationTest` 连续失败两轮，逐一修复：

### 失败 1 → 修复 1：迁移测试不再依赖 assets

- 现象：`java.io.FileNotFoundException: Missing file: com.enanke.awardledger.data.AppDatabase/1.json`
  —— `MigrationTestHelper.loadSchema` 需要从测试 APK 的 assets 读取 v1 schema，而 AGP 在 Robolectric 单元测试里不把 `test` 源集的 assets 合并进去。
- 修复：
  - `AppDatabaseMigrationTest` 改为用 `FrameworkSQLiteOpenHelperFactory` 按 v1 DDL（逐条取自 `app/schemas/…/1.json`）**自建 v1 库**，直接调用真实的 `AppDatabase.MIGRATION_1_2.migrate(db)`。
  - 删除 `app/build.gradle.kts` 中无效的 `sourceSets { getByName("test").assets.srcDir(schemas) }`。
  - 新增 `testImplementation("androidx.sqlite:sqlite-framework:2.4.0")`（提供 `FrameworkSQLiteOpenHelperFactory`）。

### 失败 2 → 修复 2：`MIGRATION_1_2` 改用「重建表 + 拷贝数据」

- 现象：`android.database.sqlite.SQLiteException: near "DROP": syntax error`
  —— `ALTER TABLE … DROP COLUMN` 需 SQLite ≥ 3.35，Robolectric 的 SQLite 不支持。
- 修复：对 `candidate_events` 与 `ledger_records` 各做一遍——
  1. `CREATE TABLE <t>_new (…)`：结构 = 原结构去掉 `occurred_at`（保留 PK / NOT NULL / 类型 / 外键）；
  2. `INSERT INTO <t>_new (<去掉 occurred_at 的全部列>) SELECT <同列> FROM <t>`：**逐列拷贝所有行，`id` 显式拷贝**，故行数、主键、成员关联（`member_id`/`member_name`）、审计引用全部不变；
  3. `DROP TABLE <t>` → `ALTER TABLE <t>_new RENAME TO <t>`；
  4. 原样重建索引：`index_candidate_events_chat_message_id`、唯一 `index_candidate_events_idempotency_key`、`index_ledger_records_member_name`、唯一 `index_ledger_records_idempotency_key`。
- 依旧**不调用 `clearAllTables()`、不执行任何 `DELETE`**，只去掉 `occurred_at` 一列。

### 迁移测试的断言范围（现已通过）

- 每表行数（members / chat_messages / candidate_events / ledger_records / audit_log）；
- 台账记录内容与成员关联（`member_id`/`member_name`/`category`/`description`/`status`/`revision`）；
- 审计日志内容保留；
- **索引**：名称 + 唯一性 + 列（`PRAGMA index_list` / `index_info`）；
- **外键**：`candidate_events.chat_message_id → chat_messages.id`，`ON DELETE CASCADE`；
- 仅 `occurred_at` 列消失；迁移本身不改成员数据；
- 另一个用例把同一份 v1 库交给 **Room 打开**，由 Room 执行迁移并做 `onValidateSchema`（结构不符会抛 `Migration didn't properly handle`）。

## 本轮已完成的改动

### 1. 名册改为「用户自行维护、仓库不含任何姓名」

- **仓库与 APK 中不再包含任何真实姓名**：`domain/Roster.kt` 已不含名单数据，改为纯文本导入逻辑 `RosterImport`（去首尾空白、忽略空行、去重）。
- 新安装为**空名册**，由用户在「名册」页自行录入；名册只保存在本机 Room 数据库。
- 内部主键仍为自增 `id`，**序号/学号不参与任何映射**。

### 2. 移除“事迹发生日期/时间”

- 领域层：`ReviewStatus` 去掉 `NEEDS_TIME`；`RawCandidate`/`ValidatedCandidate`/`LedgerRecordDto` 去掉时间字段；删除 `TimeParser` 与其测试。
- AI 层：提示词去掉 `occurredAt` 规则；`StructureParser` 去掉该字段；`DemoAiGateway` 同步。
- 数据层：`candidate_events`/`ledger_records` 去掉 `occurred_at` 列；审计快照去掉该字段；`LedgerRecordDao` 排序改为 `id DESC`。
- UI：确认/更正表单、台账/查询展示均不再出现发生时间；待处理仅显示**发送时间**（消息时间戳，非事迹时间）。
- 不再因缺少发生时间而追问、阻止入账或置为待补充；不为缺失时间推断任何值。
- 保留的技术时间戳：`chat_messages.sent_at`（发送时间）、`ledger_records.created_at`、`audit_log.timestamp`，仅用于内部排序/审计，不作为事迹信息展示。

### 3. Room 迁移与旧成员隔离（升级安全）

- `AppDatabase` 版本 1 → 2；`MIGRATION_1_2` **只去掉 `occurred_at` 一列**，采用上述「重建表 + 拷贝数据」。
- **不删除任何行、不调用 `clearAllTables()`**：成员、聊天消息、候选、台账记录、审计日志全部原样保留（行数、主键、成员关联、索引、外键均不变）。
- 名册变更是**用户显式操作**（新增/改名/停用/启用），每步都写审计日志；不存在任何自动“隔离/停用”既有成员的初始化逻辑。
- 所有可选名单查询只取 `active = 1`；停用只改标记，**成员行与 id 保留**，既有记录的历史关联与内容不变。

### 4. 品牌（显示名称 + 图标）

- 显示名称：`app_name` = 客观存在（Manifest 的 `application`/`activity` 两处 label 均引用它）。
- 图标：**已改为原创矢量自适应图标**（不再使用任何位图插画）
  - `drawable/ic_launcher_foreground.xml`：纯几何路径（圆角矩形聊天气泡 + 折线勾选），108dp 画布，图形全部落在中心 66dp 安全区内。
  - `values/colors.xml`：`ic_launcher_background` = `#1E3A5F`（纯色背景）。
  - `mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml`：background → `@color/ic_launcher_background`，foreground / monochrome → `@drawable/ic_launcher_foreground`。
  - **不含任何第三方图片、商标、角色或字体**，可随源码公开。
  - 详见 `docs/ICON.md`。

### 5. 聊天顶部署名「由谙那刻独立开发」

- **改动文件（仅 2 个）**：`app/src/main/java/com/enanke/awardledger/ui/screen/ChatScreen.kt`、`README.md`。
- **位置**：现有顶部状态行所在 Row 的**左侧列**内、状态文字**下方**——与右侧「重试待处理 / 连接设置」按钮同处一行；是**普通布局内容**，非悬浮层、不覆盖任何控件。
- **样式**：`MaterialTheme.typography.labelSmall` + `colorScheme.onSurfaceVariant`（克制小字 + 次要文字色，浅色/深色主题下均有足够对比度）。
- **布局影响**：该 Row 的高度本就由右侧 `TextButton`（M3 最小高度 40dp）决定；左侧两行文字（状态行 + 署名）合计高度约 40dp，未超过按钮高度，故**行高不变**，下方 `LazyColumn(Modifier.weight(1f))` 与底部输入 Row 的空间及滚动行为不受影响，不压缩、不遮挡任何交互控件。
- **验证**：由用户在本机完成——代码经人工核对（位置/样式/非覆盖层），并据此重跑 `testDebugUnitTest`（50 tests / 0 failures）与 `assembleDebug`（`BUILD SUCCESSFUL`，产出上述新 APK）。**未做真机/模拟器渲染确认**。
- **未改动**：业务逻辑、名单、应用名/图标、DeepSeek 配置、依赖、其他界面。

### 6. 名册管理界面（新增「名册」页）

- 新增 `ui/screen/RosterScreen.kt`、`ui/viewmodel/RosterViewModel.kt`；底部导航新增第 7 个入口「名册」（`ui/Screen.kt`、`ui/AwardLedgerRoot.kt`）。
- 能力：
  - **批量导入 / 补充**：多行粘贴，去首尾空白、忽略空行、空姓名不接受；与活动成员同名者跳过 → **重复导入幂等**；结果说明新增/跳过/忽略数量。
  - **单独新增**：姓名 +（可选）区分信息。
  - **改名**：**保持 member id 不变**，且**不改动台账记录中的历史姓名快照**。
  - **停用 / 启用**：**停用替代删除**，成员行与 id 保留。
- 同名规则：活动成员中已有同名者时，新增/改名**必须**填写与既有者不同的「区分信息」，否则**明确拒绝并说明**（绝不猜测）；录入时若匹配到多个同名成员，仍走既有 `NameMatcher` 的 AMBIGUOUS 流程要求**显式选择**。
- 数据层：`LedgerRepository` 新增 `importMembers` / `addMember` / `renameMember` / `setMemberActive` / `allMembersFlow`，均在同一事务内写 `audit_log`（`entity_type = MEMBER`，before/after 为 `MemberSnapshot` JSON）；`MemberDao` 新增相应查询并移除 `deactivateActiveExcept`。
- 初始化语义：`ensureSeeded()` → `ensureInitialized()`，**不写入、不重置任何名单**；新装为空名册；已有数据库的现有成员一律不触碰。
- **验证**：本机 `testDebugUnitTest` → `BUILD SUCCESSFUL`（XML 汇总 **58 tests / 0 failures**）；`assembleDebug` → `BUILD SUCCESSFUL`（产物见上方结论）。**未做真机安装/渲染确认。**

### 7. 开源准备

- **移除全部真实姓名**：`domain/Roster.kt` 不再包含任何名单数据；测试与文档中的名单断言一并删除；测试统一改用「张三/李四/王五」等虚构占位名。
- `.gitignore` 加固：`build/`（任意层级）、`*.apk`/`*.aab`/`*.ap_`/`*.dex`、`local.properties`、`*.db`/`*.sqlite*`/`*.log`、`*.keystore`/`*.jks`/`secrets.properties`/`.env`。
- `README.md` 重写：新增「首次配置名册（重要）」「数据与隐私」「贡献」「许可证（待定，尚未创建 LICENSE）」；`docs/ARCHITECTURE.md` §6 与 `docs/TEST_PLAN.md` 同步。
- **未创建** `LICENSE`、未新建仓库、未 `commit`/`push`、未上传或发布。
- **验证**：全仓库扫描确认源码 / 测试 / 文档中已无任何真实姓名、也不含任何本机名单文件路径（只剩“不使用学号/序号”这类政策表述）；本机测试与打包均通过。

### 8. 许可证 + 矢量图标 + 发布前扫描

- **LICENSE**：新增仓库根目录 `LICENSE`，标准 **MIT**，版权行 `Copyright (c) 2026 谙那刻`；`README.md` 的「许可证」章节改为 MIT 说明（含“保留版权与许可声明”“按现状提供”与第三方依赖各自许可）。
- **图标矢量化**：`drawable/ic_launcher_foreground.xml` 改为原创几何路径（聊天气泡 + 勾选），`values/colors.xml` 背景色改 `#1E3A5F`，两个 `mipmap-anydpi-v26` XML 改指 `@color`/`@drawable` 并加 `monochrome`。旧的 5 密度 × 2 张 PNG 因此**不再被引用**。
- **旧图标资产处置**：10 个 PNG **无法由我删除**（本会话无删除能力、`pwsh` 被拦），已在 `.gitignore` 中显式排除这两类 PNG 以免误入库；`tools/make_launcher_icon.ps1` 已改写为**废弃说明**（原脚本会覆盖矢量定义，且其默认参数含本机路径）。
- **个人路径清理**：`local.properties.example` 中原本机 SDK 绝对路径已替换为通用示例；`docs/ICON.md` 中源插画文件名与 sha256 已移除。
- **扫描结果**：源码 / 测试 / 文档 / 构建脚本中**未发现** API key、令牌、私钥、`.env` 等密钥字样（已按 `sk-`/`Bearer`/`AIza`/`AKIA`/`-----BEGIN`/`api_key=` 等模式扫描，无命中）；个人绝对路径仅存在于**已忽略**的 `local.properties`、`.gradle/`、`build/` 与 `app/build/` 生成物中。
- **未做**：未 `git init`、未 `commit`/`push`、未创建或公开 GitHub 仓库、未部署 Worker。

## 历史记录（更早代码，非本次）

下列结果来自**更早版本**的代码（在名单替换 / 移除发生时间 / 品牌改动之前），仅作历史参考，**不适用于当前代码**：

```
> Task :app:assembleDebug      BUILD SUCCESSFUL
> Task :app:testDebugUnitTest  BUILD SUCCESSFUL  (54 tests completed, 0 failed)
```

当时的网络处理：`dl.google.com` 本地 DNS 被指向黑洞 `198.18.0.167`，直连不可达；经系统代理（`127.0.0.1:7890`）可达。JVM 不继承系统代理，故对 Gradle 命令临时传 `-Dhttps.proxyHost/-Dhttps.proxyPort`，并给测试 fork JVM 设 `JAVA_TOOL_OPTIONS`（仅命令行/环境变量，未写入仓库）。测试曾出现 Robolectric `android-all-instrumented` 的 SHA-512 校验失败，系测试 fork JVM 未走代理所致，改用 `JAVA_TOOL_OPTIONS` 后通过。

## 遗留项（非阻断，未影响本次测试通过）

1. **占位文件**：`domain/FictionalRoster.kt`、`domain/TimeParser.kt`、`test/.../TimeParserTest.kt` 已被改写为**仅注释、无代码**（不定义任何类），但未被物理删除（本次改动无删除能力）。建议手动删除。
2. **本机构建目录内含历史测试数据**：`build/adb-test-run-20261011/e2e-results.txt`（早期真机联调输出）含旧名单字样。该目录已被 `.gitignore` 的 `build/` 排除，**不会入库**；但它仍在磁盘上，建议手动删除或确认不会被拷贝分享。
3. **无引用资源**：旧矢量 `drawable/ic_launcher_foreground.xml` 与 `values/colors.xml` 中的 `ic_launcher_background` 颜色已不被任何资源引用，可安全删除（保留不影响构建）。
4. **测试依赖**：`testImplementation` 现含 `androidx.sqlite:sqlite-framework:2.4.0`（`FrameworkSQLiteOpenHelperFactory`）；`androidx.test:core` 亦保留（当前迁移测试未直接使用 `InstrumentationRegistry`，无副作用）。
5. **未验证项**：已用 `aapt dump badging` 校验 APK 元数据（包名 / compileSdk·targetSdk 35 / application-label「客观存在」/ 图标 `mipmap-anydpi-v26/ic_launcher.xml`），但**未做真机或模拟器的安装与启动验证**，因此「桌面与安装器实际渲染的名称与图标」「聊天顶部署名的实际渲染」「真机首启时的 v1→v2 迁移」尚未在设备上确认。**本轮把图标换成矢量后，图标渲染同样尚未在设备上确认。**
6. **APK 产物文件名**：本轮重新打包后，产物目录中同时期存在的 `客观存在.apk` 已被默认命名的 `app-debug.apk` 取代（本报告上方结论记录的即为 `app-debug.apk`）。
7. **旧位图图标仍在磁盘**：`app/src/main/res/mipmap-*/` 下 10 个 PNG 已不再被引用且已被 `.gitignore` 排除，但**我无法删除文件**，建议手动删除。

## 遵守的约束

- 未修改工作区中其它任何项目目录（例如同级的 screen-clicker 子项目）。
- 未部署 Worker、未导入其他数据、未修改系统 ACL、未扩大 Harness 权限；**本机验证过程中也没有修改 ACL 或切换访问模式**。
- `testDebugUnitTest` 与 `assembleDebug` 均由用户在**本机**执行，产出上述 **debug APK**；**Harness 本身未运行任何命令**（其 `pwsh` 被工作区权限拦下，见上）。
- 未执行安装、部署或代码改动。
- 依赖仓库保持官方源，未添加任何第三方镜像。
