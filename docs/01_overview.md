# Omnisearch v2 — 项目概述

## 一句话定义

游戏内MC百科搜索引擎，纯客户端mod，不切屏即可搜索和阅读mcmod.cn内容。

## 项目背景

Omnisearch是一个已发布的Minecraft mod，当前状态：

- **GitHub仓库**：https://github.com/c3115644151/omnisearch
- **Modrinth**：https://modrinth.com/mod/omnisearch （2597下载，slug: omnisearch）
- **CurseForge**：作者cy311，231下载
- **MC百科**：73推荐 / 58收藏 / 3534浏览
- **许可证**：MIT
- **核心问题**：全版本搜索功能已失效（mcmod.cn安全策略升级，CAPTCHA拦截爬取）

### 当前仓库状态

仓库有7个分支，已做过初始化清理：

| 分支 | MC版本 | 加载器 | 说明 |
|------|--------|--------|------|
| main | 1.21.1 | NeoForge 21.1.212 | 基线 |
| 1.21.2 | 1.21.2 | NeoForge 21.2.0-beta | API适配差异 |
| 1.21.5 | 1.21.5 | NeoForge 21.5.95 | +RenderCompat |
| 1.21.6 | 1.21.5(?) | NeoForge 21.5.95 | gradle版本号未更新 |
| 1.21.8 | 1.21.8 | NeoForge 21.8.51 | +RenderCompat |
| 1.21.10 | 1.21.10 | NeoForge 21.10.35-beta | +RenderCompat |
| forge-1.20.1 | 1.20.1 | Forge 47.1.46 | MDK提升到根目录 |

### 当前源码文件（main分支）

| 文件 | 行数 | 职责 |
|------|------|------|
| KeyBinds.java | 16 | 按键定义 |
| Omnisearch.java | 21 | @Mod入口 |
| OmnisearchClient.java | 17 | 空壳（只有一行log） |
| ClientEvents.java | 110 | 事件分发+Tab长按逻辑 |
| ClickableEntry.java | 64 | 可点击区域 |
| HtmlRenderer.java | 990 | HTML解析+布局+渲染（God类） |
| ModernUI.java | 361 | 杂项渲染函数+UIContext |
| OmnisearchScreen.java | 556 | Screen主类（God类） |
| FetchResult.java | 36 | 搜索结果封装 |
| ItemData.java | 19 | 物品数据record |
| SearchResult.java | 32 | 搜索条目 |
| ImageManager.java | 86 | 异步图片加载 |
| McmodFetcher.java | 158 | MC百科爬取 |

可通过 `gh api repos/c3115644151/omnisearch/contents/<path>?ref=main` 查看任意文件源码。

### 旧代码的三个结构性病根

1. **HTML字符串贯穿全栈**：McmodFetcher拿到HTML → HtmlRenderer直接解析渲染，没有中间数据模型。缓存只能存原始HTML（体积大、结构不可控），UI绑定HTML结构（改网页就崩），测试需要整个MC渲染环境。

2. **Screen是上帝类**：OmnisearchScreen 556行塞了搜索状态、结果列表、详情页、动画、滚动、点击、历史——全是布尔标记和if-else。加一个功能就要在556行里找到对的位置改。

3. **7个分支各复制一份代码**：改一个bug改7次。RenderCompat是运行时反射的补丁，不是架构层面的解决。

### MapleSugar365的fork

用户MapleSugar365 fork了仓库并实现了CAPTCHA支持（分支hmmcm-hotfix）：
- **Fork地址**：https://github.com/MapleSugar365/omnisearch
- **已发出的Issue**：https://github.com/MapleSugar365/omnisearch/issues/1 （等待回复）
- **关键实现**：CaptchaInfo.java + 验证码UI + McmodFetcher重写

可通过 `gh api repos/MapleSugar365/omnisearch/contents/<path>?ref=hmmcm-hotfix` 查看fork源码。

## 重写决策

修补成本高于重写。原因：上述三个病根都要求数据流从根本上变，而旧代码没有测试、没有文档、7个分支各复制一份——没有安全网，换承重墙时随时塌。

**代码从零重写，以下知识和方案全留：**

| 保留项 | 来源 | 说明 |
|--------|------|------|
| CAPTCHA处理方案 | MapleSugar365 fork | CaptchaInfo.java + 验证码UI + McmodFetcher重写，已验证可行 |
| mcmod.cn HTML结构知识 | 旧HtmlRenderer 990行 | DOM长什么样、哪些元素需要提取。可通过gh api查看 |
| 交互流程 | 旧OmnisearchScreen | 悬停长按搜 → 结果列表 → 详情页 → 历史返回，产品逻辑已跑通 |
| 多版本API断点差异 | 旧7分支对比 | 各NeoForge/Forge版本的API差异点已梳理，详见07_multiversion.md |

## 重写原则

- **不回退**：功能不砍，体验不降级，数据源不绕开
- **站在已验证的肩膀上**：CAPTCHA方案已跑通，直接参考改进
- **设计先行**：Phase 0完成前不写Java代码
- **CRAAP+SIFT**：所有技术主张必须有验证，不臆想。验证状态见08_verified_tech.md

## 目标平台

| 维度 | 范围 |
|------|------|
| MC版本 | 1.20.1 + 1.21.1 ~ 1.21.10 |
| 加载器 | NeoForge（主）+ Forge（1.20.1） |
| 环境 | 纯客户端 |
| 数据源 | mcmod.cn（主），CAPTCHA是预期常态而非异常 |

## 文档索引

| 文件 | 内容 |
|------|------|
| `01_overview.md` | 本文件——项目背景、现状、重写决策 |
| `02_architecture.md` | 架构设计（数据流、模块划分、接口定义） |
| `03_data_model.md` | 文档对象模型（Document节点树设计） |
| `04_ui_design.md` | UI/UX设计方向 |
| `05_state_management.md` | 状态管理与导航 |
| `06_data_source.md` | 数据源层（Fetcher、Parser、Repository、Cache） |
| `07_multiversion.md` | 多版本构建策略（Stonecutter） |
| `08_verified_tech.md` | 已验证技术主张清单（附验证来源） |
| `09_open_questions.md` | 待验证/待确认问题 |
| `10_dev_phases.md` | 开发阶段规划 |
