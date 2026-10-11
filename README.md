# 奖惩台账（award-ledger-android）

一款**仅面向 Android 15（API 35）** 的聊天式班级奖惩台账应用。班干部像发消息一样用中文描述事迹，应用离线持久化原话、联网后用 DeepSeek 结构化，经严格名单核对后入账。

## 核心保证（设计红线）

1. **AI 只解析/措辞，不写台账**：模型只把原话拆成候选（姓名、奖/惩、事迹）；是否入账完全由应用按名单确定性核对。
2. **姓名严格匹配，绝不猜测**：只接受与名单**完全一致**且**唯一**的姓名；姓名不明确、名单外、同名一律追问，绝不模糊匹配/补全/用昵称。
3. **绝不使用学号/序号**：全应用仅用姓名 + 内部成员 id 关联；名册只保存姓名（可选区分信息）。
4. **多人同句拆分逐项核对**：一条消息可拆成多个候选，每个独立核对/入账。
5. **查询逐条对应**：查询只展示**数据库确定性生成**的编号正文（含真实记录 ID、姓名、类别、描述），不经过 AI，模型无法增删/编造/张冠李戴。
6. **更正保留原记录**：更正产生新记录并标记原记录作废，变更关系（supersedes）与审计日志在同一事务写入，支持把记录归属更正为另一成员。
7. **幂等**：重复发送/重试/重复入账均通过唯一 `clientMessageId` / `idempotencyKey` 去重。
8. **可恢复**：写入中断后残留的 `PROCESSING` 会在启动时重置为待处理；提供一致性核查。
9. **名册不外置**：**仓库不含任何真实姓名**；名册只存在于设备本地数据库，新安装为空，由使用者自行录入。

## 界面

底部导航七个界面：**聊天 / 待处理 / 待确认 / 更正 / 查询 / 台账（含审计日志）/ 名册管理**。

聊天主界面顶部（现有状态行所在的那一行）以次要小字显示署名「**由谙那刻独立开发**」：

- 它是**普通布局内容**，不是悬浮层，不覆盖任何控件；
- 位于该行左侧列的「状态行」下方，**复用该行已由右侧按钮确定的纵向空间**，因此不额外占用高度，消息列表与输入框仍完整可见、正常滚动；
- 样式为 `MaterialTheme.typography.labelSmall` + `colorScheme.onSurfaceVariant`。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3）
- Room / SQLite（事务、幂等、审计、迁移）
- DeepSeek API（通过云端代理转发，**API key 绝不在 APK/仓库**）
- kotlinx-serialization、OkHttp、Coroutines/Flow

## 目录结构

```
award-ledger-android/
├── app/                         # Android 应用模块
│   └── src/
│       ├── main/java/com/enanke/awardledger/
│       │   ├── domain/          # 纯领域逻辑（可 JVM 单测）：NameMatcher/StructureValidator/QueryNumbering/RosterImport
│       │   ├── data/            # Room 实体/DAO/仓库/迁移
│       │   ├── ai/              # DeepSeek 网关/提示词/解析/演示网关
│       │   ├── core/            # 依赖容器/设置/网络观察
│       │   └── ui/              # Compose 界面/ViewModel
│       ├── test/                # JVM 单元 + Robolectric 集成测试
│       └── androidTest/         # （预留）instrumented 测试
├── proxy/                       # 云端代理（Cloudflare Worker，可选）
├── tools/                       # 启动器图标生成脚本
├── docs/                        # 架构/代理配置/测试计划/构建报告
└── gradle/                      # 版本目录 + wrapper 配置
```

## 快速开始

前提：JDK 17、Android SDK（API 35）、Gradle 8.11。

```bash
# 首次：生成 wrapper jar（仓库不含该二进制）
gradle wrapper --gradle-version 8.11.1
# 本机 SDK 路径（该文件已被 .gitignore 忽略，绝不入库）
cp local.properties.example local.properties   # 填入 sdk.dir

./gradlew testDebugUnitTest    # 运行 JVM + Robolectric 测试（不产出 APK）
./gradlew assembleDebug        # 构建 debug APK
```

### 首次配置名册（重要）

**新安装的名册是空的**，应用不会写入任何内置名单：

1. 打开「**名册**」页；
2. 在「批量导入 / 补充」里粘贴姓名（**每行一个**，也可用逗号/顿号分隔），点「导入」；
   - 自动去掉首尾空格、**忽略空行**、空姓名不接受；
   - **重复导入幂等**：已存在的姓名会被跳过并在结果里说明，不会重复创建。
3. 也可以「单独新增」一个成员：填姓名，必要时填「区分信息」。

**同名（重名）规则**：

- 若名单中已存在同名成员，单独新增/改名时**必须**填写与既有同名者不同的**区分信息**，否则明确拒绝并说明原因——**绝不猜测、绝不静默合并**；
- 录入事迹时若匹配到多个同名成员，应用会要求你**显式选择**是哪一位（按内部成员 id 关联），不会自行猜测。

其余可选配置：聊天页顶部「**连接设置**」填入代理**根地址**与访问令牌（未配置则为本地演示模式）。详见 `docs/PROXY_SETUP.md`。

## 数据与隐私

- 名册、聊天原话、候选、台账记录与审计日志**只保存在设备本地的 Room 数据库**（`award_ledger.db`），不上传、不入库。
- 仓库内**不含**任何真实姓名、学号等身份信息；`local.properties`、`*.db`、`build/`、APK 等均已在 `.gitignore` 中排除。
- 升级（Room 迁移）**只做结构变更**：不调用 `clearAllTables()`、不删除任何行、不重关联旧记录；现有名册、消息、候选、台账与审计一律保留。
- **停用替代删除**：停用成员只改 `active` 标记，成员行与 id 保留，台账历史姓名快照不变；改名同样保持 member id 与历史快照。改名/停用/启用/新增都会写审计日志。

## 贡献

1. Fork / 新建分支，保持改动聚焦；
2. 代码风格：Kotlin 官方风格，注释用中文说明“为什么”；
3. **必须补测试**：领域逻辑放 `app/src/test`（纯 JVM），涉及 Room/UI 的用 Robolectric；
4. 提交前本地跑 `./gradlew testDebugUnitTest` 全绿；
5. 提交信息写清动机与影响面；
6. **不要提交**：真实姓名/学号等身份信息、API key 或访问令牌、`local.properties`、数据库文件、构建产物（APK/AAB）。
7. 不引入学号/序号匹配；不新增会重置或清空用户数据的初始化逻辑。

## 许可证

本项目采用 **MIT License**（宽松许可），完整文本见仓库根目录 `LICENSE`。

- 版权声明：`Copyright (c) 2026 谙那刻`
- 你可以自由使用、复制、修改、合并、发布、分发、再授权和/或销售本软件，
  **前提是保留上述版权声明与本许可声明**；
- 软件按“现状”提供，**不附带任何明示或默示担保**；
- 内置的第三方依赖（AndroidX / Kotlin / OkHttp 等）仍遵循各自许可证，见 `gradle/libs.versions.toml`。

## 图标与素材

- 启动器图标为**原创矢量自适应图标**：`app/src/main/res/drawable/ic_launcher_foreground.xml`
  （聊天气泡 + 勾选）配合 `app/src/main/res/values/colors.xml` 的背景色，
  经 `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` 组装。
- 全部为几何路径，**不含任何第三方图片、商标、角色或字体**，可随源码一并公开。

详见：
- `docs/ARCHITECTURE.md` — 架构与数据流
- `docs/PROXY_SETUP.md` — 代理与 API key 配置
- `docs/TEST_PLAN.md` — 测试场景与回归清单
- `docs/BUILD_REPORT.md` — 实际构建/测试结果与遗留项
- `docs/ICON.md` — 启动器图标与显示名称
