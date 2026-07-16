# 工作流：mc-mod-dev

**场景界定**：适用于基于 NeoForge 的 Minecraft Java 版 Mod 全流程开发，覆盖从需求定义、架构设计、编码实现（注册/逻辑/数据/资源）、测试验证到构建发布的完整链路。预留跨平台（Fabric/Forge/基岩版）扩展空间。

**目标产出**：可发布的 Mod JAR 文件 + 配套的 Spec/Design 文档 + GameTest 测试套件。

**设计标准**：
- 参数化变量：`{mod_id}`, `{mod_name}`, `{mod_version}`, `{package}`, `{mc_version}`, `{loader}`, `{neo_version}`
- **版本确认规则**：步骤 0 中必须先向用户确认目标 MC 版本号，然后按版本找到对应的 NeoForge MDK 仓库。NeoForge 版本号规则：
  - MC 1.21.x → NeoForge 21.x → MDK 仓库名 `MDK-{mc_version}-NeoGradle`
  - MC 26.1.x → NeoForge 26.1.x → MDK 仓库名 `MDK-{neo_version}-NeoGradle`（MC 26.1 需要 Java 25）
  - 从 https://github.com/NeoForgeMDKs 搜索 `MDK-{version}-NeoGradle` 确定最新可用模板
  - **注意**：`.bbmodel` 文件的 `java_block_version` 是 BlockBench 导出引擎版本，不是目标 Minecraft 版本，不可作为版本选择的依据
- **技术验证要求**：所有涉及外部工具调用的硬提示（如 MCP 工具命令、命令行参数、API 调用方式），必须先实际测试验证可行后再写入工作流
- 禁止批量交付——每个功能模块完成后必须交付人类确认，经确认后才能继续下一模块

**文档驱动规则（全流程适用）**：
- 任何时候需要调用 API、实现机制、排查问题时，**禁止**凭训练数据或网络搜索猜测 API 签名或机制实现方式
- 正确做法：先获取 NeoForge 文档目录树（https://raw.githubusercontent.com/neoforged/Documentation/main/docs/），了解当前版本有哪些可用文档主题，再按需逐页拉取原始 Markdown（https://raw.githubusercontent.com/neoforged/Documentation/main/docs/{category}/{topic}.md）
- 此规则贯穿所有步骤——无论是编码、debug、还是游戏内审查发现预期不符时，都应优先从官方文档确认正确实现

**上下文管理**：每步产出必须写入指定文件路径。后续步骤从文件读取前序产出，不依赖 AI 上下文记忆。

## 流程

### 步骤0：环境就绪检查（启动前置）

- **交互方式**：AI 依次检查以下环境工具，缺什么补什么，全部就绪后才进入步骤 1。
  - **版本确认**：必须先向用户确认目标 MC 版本号和 NeoForge 版本号。这是后续所有步骤的前置条件。
    - MC 1.21.x → NeoForge 21.x → JDK 21+ → MDK：`MDK-{mc_version}-NeoGradle`
    - MC 26.1.x → NeoForge 26.1.x → JDK 25 → MDK：`MDK-{neo_version}-NeoGradle`
  - **Java JDK**：运行 `java -version` 检查。
  - **NeoForge 项目骨架**：检查项目根目录是否存在 `build.gradle`、`settings.gradle`、`gradle.properties`。缺失则从 NeoForgeMDKs GitHub 组织克隆对应版本的 MDK 模板仓库。使用 `-c http.proxy="" -c https.proxy=""` 绕过代理。
  - **Gradle Wrapper**：检查 `gradlew`/`gradlew.bat` 是否存在，Gradle 版本是否符合要求（26.1 需要 Gradle 9.1+）。
  - **Python 3.10+**：运行 `python --version` 检查。
  - **Pillow**：运行 `python -c "from PIL import Image"` 检查。
  - **BlockBench + MCP 插件**（可选）：仅在需要步骤 6 中 BlockBench 交互路径时强制执行。检查 BlockBench 桌面版是否已安装，MCP 插件是否已加载。
- **边界**：所有检查项均为硬性要求。任何一项缺失都必须修复后才能继续。
- **产出文件**：无。

### 步骤1：需求定义

- **交互方式**：AI 引导用户描述 Mod 概念 → 追问关键问题（目标 MC 版本、核心功能、参考模组、技术约束）→ WebSearch 调研竞品/参考模组 → 起草 Spec → 用户审阅确认。
- **边界**：AI 决定文档格式和信息组织方式；用户决定功能范围、目标版本和优先级排序。
- **产出文件**：`spec/requirements.md`

### 步骤2：架构设计

- **交互方式**：AI 读取 `spec/requirements.md` → 获取 NeoForge 结构化指南等文档 → 设计包结构、Registry 蓝图、组件依赖树 → 起草设计文档 → 用户确认关键决策。
- **边界**：AI 决定包命名和代码组织方式；用户决定模块拆分方案、平台抽象策略。
- **产出文件**：`design/architecture.md`

### 步骤3：注册系统编码

- **交互方式**：按功能模块逐个执行：① 获取目标 MC 版本的 Registries/Items/Blocks/Entities 等文档 → ② 提取实际 API 签名和注解用法 → ③ 编写 DeferredRegister 注册代码 → ④ gradlew 编译验证 → ⑤ 交付用户确认。每完成一个模块进入下一个。
- **边界**：AI 决定属性值和注册 ID 命名；用户决定新增或跳过哪些内容。
- **产出文件**：`src/main/java/{package}/registry/` 下的注册类；`src/main/java/{package}/block/`、`item/`、`entity/`、`blockentity/` 下的类文件

### 步骤4：业务逻辑编码

- **交互方式**：按机制模块逐个执行：① 获取相关 Events/DataComponents/DataAttachments/Networking 等文档 → ② 确认 API 签名 → ③ 编写事件监听器和业务逻辑 → ④ gradlew 编译验证 → ⑤ 交付用户确认。
- **产出文件**：`src/main/java/{package}/event/`、`network/`、`datastorage/` 下的业务逻辑代码

### 步骤5：数据生成

- **交互方式**：① 获取 DataGen/Recipes/LootTables/Tags/Advancements 等文档 → ② 编写对应的 Provider 类 → ③ 运行 `gradlew runData` 生成 JSON → ④ 交付确认。
- **产出文件**：`src/main/java/{package}/data/` 下的 Provider 类；`src/generated/resources/` 下的 JSON

### 步骤6：资源构建

根据美术资源来源和导出格式分路径执行：

**① 确定来源**
- **A — 无外部美术资源**：AI 用 Python + Pillow 程序化生成 16x16 PNG 纹理 + 编写 JSON 模型和 blockstate。适用于纯程序化纹理的简单方块。
- **B — BlockBench 实时协作（MCP）**：通过 `mcp_blockbench` 工具集与 BlockBench 交互（`create_project` / `create_texture` / `place_cube` / `modify_cube` 等），用户通过截图确认视觉效果后导出。
- **C — 已有 .bbmodel 美术文件**：用户在 BlockBench 中打开 `.bbmodel` 文件，AI 通过 MCP 确认加载状态（`get_project_info`）后执行导出。

② 确定导出格式（仅 B/C 路径需要选择）
- **默认：JSON（Java Block/Item Model）**
  - 适合绝大多数家具和装饰方块
  - 导出命令：`export_model(codec_id="java_block", path=...)`
  - 纹理导出：`get_texture` + base64 解码写入
- **备选：OBJ（neoforge:obj 加载器）**
  - 何时选用：模型需要三角面/曲面等非长方体几何、元素坐标超出 JSON 的 [-16, 32] 限制、不需要每面 cullface 或 tintindex
  - 导出命令：`export_model(codec_id="obj", path=...)`
  - 纹理导出：`get_texture` + base64 解码写入（同 JSON 路线）
  - **必须先阅读** `工作流/refs/obj-deployment.md` 并按其中清单完成后处理

**③ 后处理（格式无关）**
- 生成 blockstate JSON
- 生成 item model JSON
- 写入纹理 PNG 到 `textures/block/`
- 编写 `lang/en_us.json + zh_cn.json`
- 交付用户确认视觉效果

**产出文件**：`assets/{mod_id}/blockstates/`、`models/block/`、`models/item/`、`textures/block/`、`lang/en_us.json + zh_cn.json`

### 步骤7：测试验证

- **交互方式**：① 获取 GameTest 等文档 → ② 编写 GameTest 用例覆盖核心功能 → ③ 运行 `gradlew gameTestServer` 执行测试 → ④ 交付用户确认。
- **产出文件**：`src/main/java/{package}/test/` 下的 GameTest 代码

### 步骤8：构建发布

- **交互方式**：① 确认版本号和 changelog → ② 运行 `gradlew build` → ③ 用 `jar tf build/libs/{mod_id}-{mod_version}.jar` 列出 JAR 内容，确认 `META-INF/neoforge.mods.toml` 存在 → ④ 用户自行上传分发平台。
- **产出文件**：`build/libs/{mod_id}-{mod_version}.jar`

### 步骤9：持续维护

- **交互方式**：① 用户报告问题或提出新需求 → ② 获取新版本 NeoForge API 变更 → ③ 评估影响范围和修改方案 → ④ 按需重复步骤 3-7 中相关的局部流程 → ⑤ 交付确认 → ⑥ 重复步骤 8 发布。
