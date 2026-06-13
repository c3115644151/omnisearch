# Omnisearch v2 — 架构设计

## 数据流总览

旧架构的数据流是一条直线：HTML字符串从Fetcher直通到Renderer，没有中间层。

```
旧：McmodFetcher → HTML字符串 → HtmlRenderer（990行，解析+布局+渲染一体）
         ↑ Screen直接调用
```

新架构插入Document作为中间表示，把管线拆成独立步骤：

```
用户输入 → SearchState → Repository → CacheLayer
                                      ↓ miss
                                   McmodHttpClient → HTTP → mcmod.cn
                                      ↓              ↓ CAPTCHA
                                   McmodParser    CaptchaHandler → 用户手动解
                                      ↓
                                   Document（结构化节点树）
                                      ↓
                                   LayoutEngine
                                      ↓
                                   RenderTree → GuiGraphics（MC原生渲染）
```

核心原则：**Document是整条管线的脊柱**。Fetcher不知道怎么渲染，Renderer不知道HTML长什么样，Screen不知道数据从哪来。

## 模块划分

```
src/main/java/com/cy311/omnisearch/
├── OmnisearchMod.java                 # @Mod入口，极简
├── keybinds/                          # 按键定义与注册
│   └── KeyBinds.java
│
├── client/                            # 客户端入口与事件
│   ├── ClientEntryPoint.java          # FMLClientSetupEvent / RegisterGuiLayersEvent
│   └── event/
│       └── TooltipEventHandler.java   # 悬停长按逻辑
│
├── search/                            # 状态与导航（纯数据，无MC依赖）
│   ├── SearchState.java               # 不可变状态数据类
│   ├── NavigationStack.java           # 页面栈：搜索→结果→详情→后退
│   └── SearchReducer.java             # 状态转换函数：event → state → newState
│
├── data/                              # 数据层
│   ├── repository/
│   │   ├── SearchRepository.java      # 门面：缓存优先，CAPTCHA不阻塞界面
│   │   └── CacheLayer.java            # 本地缓存（文件系统或内存）
│   ├── source/                        # 可插拔数据源
│   │   ├── DataSource.java            # 接口：search() + getPage()
│   │   ├── McmodDataSource.java       # mcmod.cn实现
│   │   └── McmodCaptchaHandler.java   # CAPTCHA检测与处理
│   ├── client/
│   │   └── McmodHttpClient.java       # 纯HTTP层，不解析
│   ├── parser/
│   │   └── McmodParser.java           # HTML → Document，Jsoup
│   └── model/                         # 数据模型（纯数据类，无MC依赖）
│       ├── SearchQuery.java
│       ├── SearchHit.java
│       ├── ItemPage.java
│       └── document/                  # 文档对象模型（详见03_data_model.md）
│           ├── Document.java
│           ├── DocNode.java           # 节点基类
│           ├── ParagraphNode.java
│           ├── TableNode.java
│           ├── ImageNode.java
│           ├── LinkNode.java
│           ├── ListNode.java
│           └── HeadingNode.java
│
├── render/                            # 渲染层
│   ├── layout/
│   │   ├── LayoutEngine.java          # Document → LayoutTree
│   │   ├── LayoutNode.java            # 布局节点（位置、尺寸）
│   │   └── LayoutContext.java         # 可用宽度、字体度量
│   ├── view/
│   │   ├── DocumentRenderer.java      # LayoutTree → GuiGraphics
│   │   ├── ParagraphView.java
│   │   ├── TableView.java
│   │   ├── ImageView.java
│   │   └── LinkView.java
│   └── image/
│       └── ImageManager.java          # 异步图片加载+纹理管理
│
├── gui/                               # MC Screen层（薄协调器）
│   ├── OmnisearchScreen.java          # ~80行，组合组件+事件分发
│   ├── component/                     # UI组件（各自独立）
│   │   ├── SearchBar.java             # 搜索输入框
│   │   ├── ResultList.java            # 搜索结果列表
│   │   ├── DetailView.java            # 详情视图（组合DocumentRenderer）
│   │   └── CaptchaDialog.java         # 验证码弹窗
│   └── animation/
│       └── SlideAnimation.java        # 滑入滑出
│
└── compat/                            # 版本兼容（Stonecutter编译期替换）
    └── VersionAdapter.java            # 不存在运行时反射
```

## 关键接口定义

### DataSource

```java
public interface DataSource {
    CompletableFuture<List<SearchHit>> search(SearchQuery query);
    CompletableFuture<ItemPage> getPage(String pageId);
    String name();  // "mcmod" / "curseforge" 等
}
```

### DocNode

```java
public abstract class DocNode {
    public abstract <T> T accept(DocNodeVisitor<T> visitor);
}
```

### SearchState

```java
public record SearchState(
    Page currentPage,           // SEARCH / RESULTS / DETAIL
    SearchQuery query,
    List<SearchHit> results,
    @Nullable ItemPage detailPage,
    NavigationStack navStack,
    LoadingState loading,       // IDLE / LOADING / CAPTCHA_REQUIRED / ERROR
    @Nullable String errorMessage
) {}
```

### SearchReducer

```java
public static SearchState reduce(SearchState current, SearchEvent event) {
    // 纯函数，无副作用
    // 返回新状态，Screen只做 state → render
}
```

## 与旧架构的对比

| 维度 | 旧架构 | 新架构 |
|------|--------|--------|
| 数据流 | HTML字符串直通 | HTML → Document → Layout → Render |
| 状态管理 | Screen内布尔标记 | 不可变数据类 + 纯函数reducer |
| 数据获取 | Fetcher直接调HTTP | Repository → Cache → Source，CAPTCHA不阻塞 |
| 渲染 | 990行God类 | 三步管线，每步独立可测 |
| 多版本 | 7个分支各复制一份 | Stonecutter编译期替换 |
| Screen | 556行上帝类 | ~80行协调器 + 独立组件 |
| 测试 | 只能在MC环境里跑 | search/data/model层纯Java，可单元测试 |

## 层间依赖规则

```
gui → render → data.model ← data.parser ← data.source ← data.repository ← search
 │                                    ↑
 └── search（状态管理读model）─────────┘
```

- **gui** 依赖 **render** 和 **search**（读状态、渲染）
- **render** 依赖 **data.model**（Document/Layout）
- **search** 依赖 **data.model**（SearchHit/ItemPage）
- **data.model** 不依赖任何层（纯数据类）
- **data.source/parser/client** 依赖 **data.model**
- **data.repository** 依赖 **data.source** 和 **data.model**

关键：data.model 是纯Java，无MC依赖，可独立编译和测试。

## 旧代码参考路径

开发过程中如需参考旧代码实现细节，可通过GitHub API查看：

```bash
# 查看main分支任意文件
gh api repos/c3115644151/omnisearch/contents/<path>?ref=main

# 查看MapleSugar365的CAPTCHA实现
gh api repos/MapleSugar365/omnisearch/contents/<path>?ref=hmmcm-hotfix

# 关键旧文件路径（src/main/java/com/cy311/omnisearch/client/gui/下）：
# - HtmlRenderer.java     (990行，HTML解析+布局+渲染)
# - OmnisearchScreen.java (556行，Screen主类)
# - ModernUI.java         (361行，杂项渲染)
# - McmodFetcher.java     (158行，MC百科爬取，在fetcher/包下)
```
