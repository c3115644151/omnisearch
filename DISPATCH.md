# Omnisearch v2 — 团队调度文档

> **日期**: 2026-06-14
> **项目根目录**: c:\Users\32800\Desktop\omnisearch
> **项目状态**: 渲染层（Wave 5）已完成，准备下一轮开发

---

## 一、项目现状

### 已完成部分
- **纯 Java 数据层**：data.model, search, data.parser, data.client, data.source, data.repository — 完整数据管线完成，287 测试全部通过
- **MC 事件入口**：KeyBinds(TAB) + ClientEntryPoint + TooltipEventHandler(TAB长按搜索)
- **UI 风格确认**：经典原版 (Classic Vanilla) — 石头灰背景 + 双层边框阴影
- **渲染层 (Wave 5)**：
  - `client/render/document/DocumentRenderer.java` — DocNodeVisitor 文档树渲染，支持全部 11 种节点类型
  - `client/render/SearchBarWidget.java` — 经典原版搜索框
  - `client/render/ResultListWidget.java` — 黑底白字结果列表
  - `client/render/DetailPanelWidget.java` — 详情容器面板
  - `client/render/CaptchaDialogWidget.java` — 验证码弹窗
  - `client/screen/OmnisearchScreen.java` — Screen 层，状态驱动视图分发（~112 行）
  - `search/SearchEvent.java` — 新增 `SearchResultsLoaded` 事件
  - `search/SearchReducer.java` — 新增 `SearchResultsLoaded` 处理
  - `client/event/TooltipEventHandler.java` — TAB 按键/长按触发打开 Screen
- **编译验证**：`gradlew compileJava` BUILD SUCCESSFUL
- **回归测试**：`gradlew test` BUILD SUCCESSFUL（所有现有测试通过）

### 存在问题
- 无游戏内实际运行测试（需要完整游戏环境）
- 渲染组件基于社区 Javadoc 验证，未在真实 NeoForge 1.21.1 环境中运行

### 技术债
- CSS 选择器基于旧代码推导，待用当前 mcmod.cn 页面验证
- Screen 渲染逻辑需在游戏内验证 EditBox 对齐、滚动条交互等

---

## 二、不可变约束

1. **Screen ≤ ~80 行**（逻辑部分，不含导入/类声明）
2. **绝不 @Overwrite** — 除非注释说明理由
3. **NeoForge API 调用必须验证签名** — 注释标注 `// verified: [source] [date]`
4. **Client 类只在本端注册**
5. **渲染组件在 client/render/ 下独立**
6. **HTML 不直接渲染** — 始终经过 Document 中间层
7. **状态不可变** — 变更通过 Reducer 返回新实例

---

## 三、数据流图

```
TooltipEventHandler (TAB长按检测)
  │ setScreen(new OmnisearchScreen(repo))
  ▼
OmnisearchScreen (Screen层)
  │ 读取 SearchState (record)
  │ 分派 SearchEvent → SearchReducer → 新 SearchState
  ▼
┌─────────────────────────────────────────────┐
│  SearchState.Page 决定当前视图               │
│  SEARCH   → SearchBarWidget (居中搜索框)      │
│  RESULTS  → SearchBarWidget + ResultListWidget│
│  DETAIL   → DetailPanelWidget                 │
│              └── DocumentRenderer (文档树渲染) │
│  CAPTCHA  → CaptchaDialogWidget (验证码弹窗)  │
└─────────────────────────────────────────────┘
```

---

## 四、历史记录

### 2026-06-13 第1-3b轮：纯 Java 数据层完成（287 测试）

### 2026-06-14 第4轮：keybinds + client/event 入口层
- **结果**：✅ 完成

### 2026-06-14 UI 风格确认
- **结果**：✅ 经典原版 (Classic Vanilla)

### 2026-06-14 渲染层 (Wave 5)
- **结果**：✅ 完成
- **Agent H (doc-renderer)**：DocumentRenderer — 完整 DocNodeVisitor 实现
- **Agent I (render-widgets)**：SearchBarWidget + ResultListWidget + DetailPanelWidget + CaptchaDialogWidget
- **Agent J (screen-layer)**：OmnisearchScreen + SearchEvent/SearchReducer 扩展 + TooltipEventHandler 集成
- **修复**：1 处编译错误修复 — `getContext()` → `getCaptchaContext()`
- **产出文件**：
  - 5 个新建渲染文件
  - 3 个修改文件（SearchEvent, SearchReducer, TooltipEventHandler）
  - 1 个新文件（OmnisearchScreen）
