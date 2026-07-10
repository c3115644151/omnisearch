# Omnisearch v2 — TAB入口悬浮窗重构设计

## 文档目的

本文档记录已经确认的 TAB 入口 UI 重构方案，作为后续实现阶段的状态锚点。

本轮重构只处理：

- TAB 打开的主搜索 UI
- 当前全屏 `OmnisearchScreen` 向右侧悬浮窗形态的迁移
- 与悬浮窗形态直接相关的组件、状态、错误处理和测试策略

本轮明确不处理：

- 物品悬停长按 TAB 的新入口设计
- HUD 常驻搜索条
- 双栏常驻结果+详情布局
- 更高阶的“会话持久化”体验

## 现状结论

当前实现的核心问题不是“界面不好看”，而是结构不匹配。

- `TAB` 目前直接打开一个全屏 `Screen`
- `OmnisearchScreen` 同时承担搜索页、结果页、详情页三种页面
- 搜索、渲染、滚动、链接点击、验证码、缓存提示都集中在同一个类中

这使得当前 UI 更像“游戏内网页浏览器”，而不是“边玩边查的辅助面板”。

## 已确认决策

### 1. 窗口形态

采用右侧悬浮窗，而不是全屏覆盖 UI。

- 锚点：屏幕右侧
- 目的：保留世界画面主体，降低“离开游戏去开网页”的割裂感
- 视觉原则：像 Minecraft 内置工作台/容器的延伸，而不是外嵌网页

### 2. 内容结构

采用“单内容区切换”方案。

- 顶部固定搜索头部
- 中部只有一个内容容器
- 内容容器在 `EMPTY / RESULTS / DETAIL` 之间切换
- 底部固定状态区

本轮不采用双栏，也不在详情页保留底部结果条。

### 3. 状态模型

采用拆分状态模型，而不是继续让 `SearchState.Page` 驱动整个 UI。

推荐拆分为：

- `WindowSessionState`
- `SearchSessionState`
- `DetailViewState`

### 4. 错误处理

采用局部错误显示，不再让错误驱动整窗切回“统一错误页”。

- 搜索错误只影响结果区
- 详情错误只影响详情区
- 验证码作为窗口内模态层处理
- 请求取消不视为错误

### 5. 测试策略

优先补“状态迁移 + 命中区 + 生命周期”测试，不做大规模脆弱截图测试。

## 布局设计

### 目标位置

右侧悬浮窗建议使用固定右锚点：

```text
x = screenWidth - panelWidth - 16
y = 20
panelWidth = clamp(420, screenWidth * 0.34, 520)
panelHeight = screenHeight - 40
```

设计目的：

- 让玩家主要视线仍留在世界中央
- 查询面板作为“右侧工作台”存在
- 为后续可能接入悬停查询保留自然落点

### 窗口层级

```text
游戏世界
  └─ 轻微暗层
      └─ FloatingSearchWindow
          ├─ SearchHeaderComponent
          ├─ BodyHost
          │   ├─ EmptyStateView
          │   ├─ SearchResultsPane
          │   └─ DetailContentPane
          ├─ StatusFooterComponent
          └─ CaptchaModalComponent（按需覆盖）
```

### 视觉约束

- 顶部搜索栏固定，不随结果/详情切换改变位置
- 返回和关闭按钮属于窗口 chrome，而不是正文区
- 正文滚动区必须独立 scissor，不能压住头部和底部状态条
- 只使用轻遮罩，不重新引入全屏压暗感

## 组件拆分

### `OmnisearchScreen`

职责收缩为：

- 承载 `Screen` 生命周期
- 组合窗口组件
- 分发输入
- 协调异步请求
- 在关闭时统一回收资源

明确不再负责：

- 直接计算详情区点击命中
- 直接维护详情布局缓存
- 直接处理每个子区域的几何逻辑

### `FloatingSearchWindow`

新增窗口壳层，负责：

- 右侧窗口矩形计算
- 头部 / 内容区 / 底部状态区 bounds
- 返回按钮和关闭按钮命中区
- 模态层覆盖区域

这是本轮最核心的新容器。

### `SearchHeaderComponent`

负责：

- 搜索框
- 标题文案
- 返回按钮
- 关闭按钮

复用现有 `SearchBarWidget` 的绘制能力，但改为固定顶部布局。

### `SearchResultsPane`

负责：

- 搜索结果列表展示
- 结果区滚动
- 行点击映射
- 空结果提示
- 结果区局部错误提示

复用现有 `ResultListWidget` 的绘制逻辑。

### `DetailContentPane`

负责：

- 详情头部
- 正文布局缓存
- 正文滚动
- 标题点击
- 来源标签点击
- 正文链接命中提取
- 详情区局部错误显示

复用现有 `DetailPanelWidget`、`DocumentRenderer`、`PreparedDocumentLayout`。

### `StatusFooterComponent`

负责：

- loading 文案
- 轻量错误提示
- 缓存已清除提示
- 当前请求状态提示

它替代当前 `OmnisearchScreen.render()` 尾部散落的状态文本。

### `CaptchaModalComponent`

负责：

- 验证码图片展示
- 输入框
- 提交按钮
- 返回原请求上下文

验证码继续存在于同一悬浮窗内，不再表现为另起一套页面。

## 数据流设计

## 状态拆分

### `WindowSessionState`

管理窗口级状态：

- 是否打开
- 窗口几何
- 当前 modal
- 底部状态条
- 会话 token

### `SearchSessionState`

管理查询会话：

- `query`
- `results`
- `selectedResultIndex`
- `currentView`
- `searchLoading`
- `searchError`
- `detailError`
- `pendingRequest`

### `DetailViewState`

管理详情专属数据：

- `detailPage`
- `detailScrollOffset`
- `cachedDetailLayout`
- `cachedDetailLinks`
- `cachedDetailWidth`
- `layoutVersionToken`

## 内容视图状态

内容区仅在以下三种状态切换：

- `EMPTY`
- `RESULTS`
- `DETAIL`

注意：内容视图不是窗口状态，也不是网络状态。

### 核心迁移规则

#### 打开窗口

- 创建右侧悬浮窗
- 搜索框获得焦点
- 内容区显示 `EMPTY`

#### 输入查询

- 仅更新 `query`
- 不切换内容区

#### 提交搜索

- 内容区切到 `RESULTS`
- `searchLoading = true`
- 保留旧结果，直到新结果返回

#### 搜索成功

- 更新 `results`
- 清除 `searchError`
- `searchLoading = false`

#### 搜索失败

- 保留现有 `query`
- 若已有旧结果，则继续显示旧结果
- 若无结果，则结果区显示局部错误视图

#### 点击结果

- 记录 `selectedResultIndex`
- 发起详情请求
- 内容区切到 `DETAIL`

#### 详情成功

- 更新 `detailPage`
- 刷新 `DetailViewState`
- 清除 `detailError`

#### 详情失败

- 保持内容区在 `DETAIL`
- 显示局部详情错误面板

#### 返回

- 仅 `DETAIL -> RESULTS`
- 不关闭窗口
- 不清空搜索词
- 不清空已有结果

#### ESC

- 关闭整个悬浮窗
- 本轮默认清空当前会话

## 异步与生命周期

现有 `searchSeq / detailSeq` 防串包机制可保留，但建议升级为双层令牌：

- `windowSessionToken`
- `requestToken`

规则：

- 关闭窗口时，所有 in-flight 请求都视为失效
- 重新搜索时，旧详情请求作废
- 快速点多个结果时，只接受最后一次详情结果

## 错误处理设计

### 分层原则

#### 窗口级

- `statusBanner`
- `modal`

用于轻提示和验证码模态。

#### 搜索级

- 网络错误
- 空结果
- 搜索请求验证码

这些错误只影响结果区。

#### 详情级

- 详情抓取失败
- 文档解析失败
- 布局准备失败

这些错误只影响详情区。

#### 渲染局部失败

- 单图标失败
- 单图片失败
- 单链接命中失败

只能局部降级，不能让整页失效。

### 已确认规则

- 请求取消不是错误，不显示红色错误提示
- 搜索错误不污染详情态
- 详情错误不污染结果态
- `CaptchaRequired` 不应继续塞进通用 `LoadingState`

## 测试与验证

## 目标

本轮重构的测试重点不是“截图长得像不像”，而是确保结构迁移后行为不退化。

## 分层

### 1. 纯状态测试

新增或重构：

- `WindowSessionStateTest`
- `SearchSessionStateTest`
- `DetailViewStateTest`
- 新的 reducer 测试

覆盖：

- 打开 / 关闭窗口
- 搜索成功 / 失败
- 结果进入详情
- 详情返回结果
- 验证码恢复上下文
- 请求取消不污染错误态

### 2. 窗口组件测试

新增：

- `FloatingSearchWindowTest`

覆盖：

- 右锚点位置计算
- 头部 bounds
- 内容区 bounds
- 底部状态条 bounds
- 返回 / 关闭按钮命中

### 3. 内容面板测试

新增：

- `SearchResultsPaneTest`
- `DetailContentPaneTest`

覆盖：

- 列表滚动
- 点击结果映射
- 详情区滚动
- 正文链接命中
- 标题和来源标签点击
- 缓存失效条件

### 4. 渲染回归测试

保留并扩展现有：

- `DocumentRendererTest`

额外覆盖：

- 新窗口偏移不破坏正文链接命中
- 详情滚动偏移后链接坐标仍正确

### 5. 生命周期测试

必测：

- 关闭窗口后旧回调被丢弃
- 宽度变化触发布局缓存失效
- 快速切换多个详情请求只接受最后一次结果

## 推荐最小落地顺序

### Phase 1：窗口壳层落地

- 引入 `FloatingSearchWindow`
- 固定顶部搜索栏
- 固定底部状态区
- 将现有全屏布局压缩进右侧窗口

### Phase 2：状态模型拆分

- 从单一 `SearchState` 迁移到拆分状态
- 将页面驱动改为内容区驱动

### Phase 3：详情区责任下沉

- 将详情布局缓存、滚动、链接命中迁入 `DetailContentPane`

### Phase 4：错误处理局部化

- 搜索错误和详情错误分离
- 验证码模态独立

### Phase 5：测试补齐与回归

- 补齐状态测试
- 补齐组件命中测试
- 跑关键回归测试

## 风险与禁区

### 风险

- 旧 `SearchState.Page` 语义与新窗口容器冲突
- 详情区滚动与新窗口坐标系叠加后，命中区容易偏移
- 若把错误重新汇总成全局错误页，会抵消本轮状态拆分价值

### 禁区

- 不要把悬浮窗做成“缩小版全屏浏览器”
- 不要先引入双栏布局
- 不要在第一轮同时做“会话持久化”
- 不要把验证码再次做成独立页面流

## 本轮结论

TAB 入口的正确演进方向不是继续优化全屏页，而是将现有搜索、结果、详情能力重组为右侧悬浮工作台。

这次重构的关键不是改皮肤，而是把：

```text
全屏三页式 Screen
```

迁移为：

```text
右侧悬浮窗容器 + 固定头部 + 单内容区切换 + 局部错误处理
```

后续实现必须以本文件为准，避免再次回到“整页路由器”思路。
