# CLAUDE.md

## 你在做什么

Omnisearch 是 MC 客户端 mod，游戏内搜索浏览 mcmod.cn。从零重写，旧代码仅作参考。

设计文档在 `docs/`，旧代码在 git tag `archive/v1.0.3-*`。

## 不可违反

1. **验证前不写代码** — MC mod API 每个小版本都可能破坏性变更。写任何加载器 API 调用前，必须通过 gh api 查源码确认签名。注释标注 `// verified: [source] [date]`
2. **Mixin 目标必须在源码中确认** — 方法名、描述符、注入点，任何一个错就崩溃。用 `mc-mod-mixin` skill 的流程。
3. **不降级、不砍功能、不绕开困难** — 遇到技术障碍攻克它，不要降级方案。CAPTCHA 已有验证可行的实现，数据源可用，原有功能全部保留。
4. **HTML 不直接到渲染** — 必须经过 Document 中间层：HTML → Document → 渲染组件。Fetcher 不碰渲染。
5. **Screen 不超过 ~80 行** — 只做布局和事件分发。逻辑在组件里，数据在 Repository 里，状态在 Reducer 里。
6. **状态不可变** — SearchState 是 record，变更通过 Reducer 返回新实例。不用布尔标记，不用可变字段。
7. **Stonecutter 管版本，不用分支** — `//? if >=1.21.5` 编译期替换，同一源码树输出不同版本 JAR。
8. **绝不 @Overwrite** — 除非有充分理由且代码注释说明。

## 你会犯的错

这些是本项目实际踩过的坑，每条都来自真实失败：

- **凭未验证的前提开发** — "Component 能渲染富文本""Architectury 支持跨平台""Jsoup 解析没问题"——这些都是曾经信以为真、验证后被推翻的命题。每次动手前问自己：这条前提验证过吗？
- **用 Component 替代 HTML 渲染** — Component 只支持文本和样式，不能渲染图片/表格/富文本。必须自建渲染组件。
- **建议 Architectury** — Forge/NeoForge 从 1.20.5 起已 broken。用 Stonecutter + 独立 JAR。
- **从头实现 CAPTCHA** — MapleSugar365 已验证可行，站在已有方案上。查看：`gh api repos/MapleSugar365/omnisearch/contents/<path>?ref=hmmcm-hotfix | jq -r '.content' | base64 -d`
- **写 God 类** — 超过 100 行的渲染类或 Screen 类就是信号，停下来拆分。
- **混淆 Forge 和 NeoForge** — 包名完全不同：`net.minecraftforge.*` vs `net.neoforged.neoforge.*`。用 `mc-mod-api` skill 查询。
- **凭记忆写 API** — 你的训练数据大概率过时。用 `mc-mod-api` skill 验证。

## 关键版本断点

- **1.20.5+**: 数据组件替代物品 NBT，注册 API 签名变更
- **1.21.5+**: 渲染管线重构
- **1.21.9+**: FML 重写，KeyMapping.Category 变 record
- **Forge 1.20.1 vs NeoForge**: 几乎所有包名不同

## 多 Agent 开发

使用 `ceo` skill。核心原则：**接缝比模块危险，隐含假设最致命**。

分工时确保：
- 模块间接口先于模块内部实现定义
- 每个 agent 拿到的上下文完整到能独立闭环
- 集成点是最高风险区域，优先验证
