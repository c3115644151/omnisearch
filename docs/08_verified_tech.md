# Omnisearch v2 — 已验证技术主张清单

本文档记录所有技术主张的验证状态，使用CRAAP+SIFT双重标准。开发agent应以此为据，未验证的主张不写入代码。

## 验证标准

- **CRAAP**：Currency时效性、Relevance相关性、Authority权威性、Accuracy准确性、Purpose目的
- **SIFT**：Stop停下思考、Investigate调查来源、Find找更好来源、Trace追踪原始说法

## 已验证主张

### ✅ Stonecutter适合Omnisearch的多版本构建

| 维度 | 评估 |
|------|------|
| 来源 | Stonecutter官方文档 https://stonecraft.meza.gg/docs |
| 时效性 | 2026年仍活跃维护 |
| 权威性 | 官方文档，一手来源 |
| 反面证据 | 无 |

### ✅ Architectury不适合Omnisearch

| 维度 | 评估 |
|------|------|
| 来源 | GitHub architectury/architectury issues；Justin Schaaf报告Forge模板问题 |
| 时效性 | 1.20.5+问题未修复 |
| 权威性 | 官方GitHub issue，一手来源 |
| 反面证据 | 部分mod（如Appleskin）仍在使用Architectury成功部署 |

### ✅ 独立JAR比Architectury更适合纯客户端mod

| 维度 | 评估 |
|------|------|
| 来源 | Xaero's Minimap的实际发布方式（每个loader独立JAR） |
| 时效性 | 2026年仍在使用此模式 |
| 权威性 | 下载量最高的纯客户端mod之一，实践验证 |
| 反面证据 | 增加发布工作量（每个loader单独构建+上传） |

### ✅ NeoForge RegisterGuiLayersEvent可用于搜索overlay

| 维度 | 评估 |
|------|------|
| 来源 | SearchingGUI mod实际使用此API（NeoForge 1.21.1-1.21.5） |
| 时效性 | 当前可用 |
| 权威性 | 一手验证（真实mod使用） |
| 反面证据 | 无 |

### ✅ CAPTCHA处理在Omnisearch场景下可行

| 维度 | 评估 |
|------|------|
| 来源 | MapleSugar365 fork已实现并发布（CaptchaInfo.java + 验证码UI），分支hmmcm-hotfix |
| 时效性 | 2026年仍在可用状态 |
| 权威性 | 实际跑通的代码，一手来源 |
| 反面证据 | CAPTCHA频率和用户体验未经验证 |

## 已推翻主张

### ❌ MC原生Component可替代HTML渲染器

| 维度 | 评估 |
|------|------|
| 来源 | Minecraft Wiki官方规范；NeoForge 1.21.10文档 |
| 时效性 | 当前仍如此 |
| 权威性 | 官方规范，一手来源 |
| 失败原因 | Component仅支持text/translatable/score/nbt/keybind五种内容类型，无图片/表格能力 |
| 影响 | 渲染层仍需自定义实现，不能依赖Component做富内容展示。但搜索结果列表（纯文字）可以用Component |

### ❌ Architectury适合纯客户端小mod

| 维度 | 评估 |
|------|------|
| 来源 | GitHub issues显示Forge/NeoForge模板从1.20.5起broken |
| 失败原因 | 维护者明确讨论弃用Forge支持，模板问题长期未修复 |
| 影响 | 使用Stonecutter + 独立JAR替代，见07_multiversion.md |

## 部分成立主张

### ⚠️ NeoForge原生Screen/GuiGraphics API够用

| 维度 | 评估 |
|------|------|
| 成立部分 | 功能上可行，SearchingGUI验证了搜索overlay |
| 不成立部分 | 无内置搜索框组件（EditBox可用但需包装）、无原生滚动列表、无详情面板布局 |
| 结论 | 可行但有工作量，不引入重型UI库（LDLib 6MB）的决策正确，自建组件需正视工作量 |

### ⚠️ Jsoup解析mcmod.cn HTML

| 维度 | 评估 |
|------|------|
| 成立部分 | Jsoup解析HTML技术成熟，旧代码就是用Jsoup |
| 不成立部分 | mcmod.cn活跃反爬（2026-06-13实测fetch返回err_code:7拦截），HTML结构可能随时变化 |
| 结论 | 技术可行，但依赖mcmod.cn的HTML结构稳定性。Document中间层（见03_data_model.md）的引入正是为了隔离这个风险 |
