# Omnisearch v2 — 待验证/待确认问题

本文档列出所有未解决的技术问题，按优先级分级。开发agent在写代码前必须解决所有P0问题。

## 关键阻塞（必须在写代码前解决）

### 🔴 P0：mcmod.cn页面HTML结构

**问题**：03_data_model.md中的HTML→Document映射是基于旧HtmlRenderer 990行代码推导的CSS选择器，未用当前页面验证。mcmod.cn可能已改版，选择器可能已失效。

**验证方法**：用浏览器手动访问mcmod.cn，查看物品页和搜索结果页的HTML源码，确认：
1. CSS选择器是否仍然有效（`.item-name`, `.item-mod`, `.item-info-table` 等）
2. 页面是否服务端渲染（决定Jsoup是否可用——Jsoup不执行JavaScript）
3. 是否有未公开的JSON API（如有，可绕过HTML解析）

**参考**：旧代码中的HTML结构知识可通过 `gh api repos/c3115644151/omnisearch/contents/src/main/java/com/cy311/omnisearch/client/gui/HtmlRenderer.java?ref=main` 查看990行HtmlRenderer中的Jsoup选择器。

**状态**：当前被安全策略拦截无法自动获取，需要云电脑或用户手动验证

### 🔴 P0：mcmod.cn CAPTCHA的实际触发频率

**问题**：CAPTCHA多久触发一次？每次搜索都触发？还是高频请求才触发？这直接决定缓存策略和用户体验设计。

**验证方法**：
1. 实际使用中观察
2. 参考MapleSugar365的fork实现中是否有相关记录（`gh api repos/MapleSugar365/omnisearch/contents/<path>?ref=hmmcm-hotfix`）

**状态**：未验证

### 🔴 P0：Stonecutter在Omnisearch项目中的实际配置

**问题**：Stonecutter的官方文档和示例都是通用场景，Omnisearch的具体配置（版本矩阵、条件标记数量）需要实际搭建验证。

**验证方法**：创建空项目，配置Stonecutter，确认构建流程通畅

**状态**：未验证

## 重要但不阻塞

### 🟡 P1：是否需要补充数据源

**问题**：CurseForge/Modrinth有公开API，可以补全英文mod信息。但Omnisearch的核心价值是mcmod.cn的中文内容。

**需要项目所有者确认**：
- 玩家反馈中是否有人需要英文mod信息？
- 补充数据源的开发成本 vs 用户价值
- CurseForge API的调用限制（[官方文档](https://docs.curseforge.com/rest-api)）

### 🟡 P1：UI组件的最终技术方案

**问题**：NeoForge原生API没有内置搜索框和滚动列表，需要自建。但自建的范围有多大？

**需要确认**：
- 搜索框：EditBox是MC原生的，应该可以直接用
- 滚动列表：MC原生的ContainerEventHandler可以处理滚动，但需要自建列表渲染
- 详情面板：完全自建

### 🟡 P1：Document序列化格式

**问题**：缓存存储Document的JSON序列化。JSON序列化库选择：
- Gson（MC自带，零依赖）
- Jackson（功能更强，但需要额外依赖）
- Moshi（轻量）

**倾向**：Gson，零依赖，MC自带。

## 可以在开发中解决

### 🟢 P2：Forge 1.20.1的代码管理方式

独立分支还是Stonecutter统一管理，等NeoForge版写完再看条件标记数量决定。旧仓库forge-1.20.1分支已做清理可作为参考。

### 🟢 P2：ImageManager的具体实现

异步图片下载+纹理管理的细节，开发Phase 2时解决。旧代码ImageManager.java 86行可参考。

### 🟢 P2：动画系统

SlideAnimation的具体实现，开发Phase 2时解决。旧代码OmnisearchScreen.java中有动画实现可参考。

## 来自验证报告的INFO GAP

1. mcmod.cn是否为服务端渲染（决定Jsoup是否可用）
2. mcmod.cn是否有未公开的JSON API
3. Architectury 17.x是否修复了Justin Schaaf报告的Forge/NeoForge模板问题
4. 旧代码HtmlRenderer/McmodFetcher中的完整HTML结构知识（需通读源码提取）
