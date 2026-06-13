# Omnisearch v2 — 团队调度文档

> **日期**: 2026-06-13
> **项目根目录**: c:\Users\32800\Desktop\omnisearch
> **本轮目标**: 实现 data.model（纯数据类） + search（状态管理），建立整条管线的基石

---

## 一、项目现状

### 已完成部分
- 设计文档完整（docs/01~09）
- 构建系统配置：NeoForge ModDevGradle 2.0.141 + Stonecutter 0.9.5（仅 1.21.1）
- CLAUDE.md 项目规则已配置
- mc-mod-api + mc-mod-mixin skills 已就绪

### 存在问题
- 源码层几乎空白：仅 OmnisearchMod.java 14 行入口类
- 语言文件为空（en_us.json = {}）
- 模板目录不存在（src/main/templates/）
- 无任何测试

### 技术债
- 无（从零开始）

---

## 二、不可变约束

### 设计/业务约束
1. **Document 是整条管线的脊柱** — Fetcher 不知道怎么渲染，Renderer 不知道 HTML 长什么样
2. **状态不可变** — SearchState 是 record，变更通过 Reducer 返回新实例。不用布尔标记，不用可变字段
3. **HTML 不直接到渲染** — 必须经过 Document 中间层：HTML → Document → 渲染组件
4. **Screen 不超过 ~80 行** — 只做布局和事件分发。逻辑在组件里，数据在 Repository 里，状态在 Reducer 里
5. **绝不 @Overwrite** — 除非有充分理由且代码注释说明

### 代码约束
6. **每个 Agent 只修改自己边界内的文件**，绝不触碰其他 Agent 的文件
7. **data.model 包必须是纯 Java** — 零 MC 依赖，零外部库依赖（不引入 Jsoup 等）
8. **所有 record 类必须用 `@Nullable` 标注可为空的字段**（jakarta.annotation.Nullable 或 jetbrains.annotations.Nullable）

### 测试纪律
9. **所有实现必须包含对应测试**。data.model 和 search 可跑纯 JUnit，不依赖 MC 环境
10. **禁止假绿**：测试必须包含边界用例。record 的构造/相等/toString 不是"测试"
11. **测试类型**：纯单元测试（data.model + search 层不需要 MC 环境）

---

## 三、协作规则

### 依赖与等待
- Agent B（search）依赖 Agent A（data.model）的数据类型。必须等 A 完成后才能开始 B
- 等待期间 Agent 不启动，由 CEO 在验收 A 后再启动 B

### 进度汇报
- 完成开发后，在本文档的"进度追踪"章节更新状态：⬜未启动 → 🔄进行中 → ✅已完成
- 更新时简要说明：实现了什么、对外接口是否和契约一致、有没有偏离规格的地方

### 禁止回退策略
- 不得采取任何降低预期效果的回退策略。遇到困难如实汇报

### 冲突处理
- 只修改自己边界内的文件
- 如果发现需要修改别人的文件才能完成自己的任务，在汇报中说明

---

## 四、依赖图与调度顺序

```
Agent A (data.model，无依赖)
  └── Agent B (search，依赖 A 的 data.model 类型)

调度顺序：
  第一波：Agent A（data.model）
  → CEO 验收 A →
  第二波：Agent B（search）
```

---

## 五、团队定义

### Agent A: data.model 纯数据类
**代号**: data-model
**文件边界**:
- `src/main/java/com/cy311/omnisearch/data/model/document/` 下所有文件
- `src/main/java/com/cy311/omnisearch/data/model/SearchQuery.java`
- `src/main/java/com/cy311/omnisearch/data/model/SearchHit.java`
- `src/main/java/com/cy311/omnisearch/data/model/ItemPage.java`
- `src/main/java/com/cy311/omnisearch/data/model/CaptchaContext.java`
- 对应的测试文件在 `src/test/java/com/cy311/omnisearch/data/model/` 下
**禁止触碰**: 任何其他包下的文件
**依赖**: 无

#### 任务背景
设计文档 03_data_model.md 和 02_architecture.md 已定义了完整数据模型。这是整条管线的基石——所有模块都依赖它。

#### 最终规格
1. 实现 Document 节点树（DocNode 为抽象基类，所有节点类继承它）
2. 实现 SearchQuery / SearchHit / ItemPage / CaptchaContext 数据类
3. 所有类在 `data.model` 包下，纯 Java，无任何外部依赖（含 MC API）
4. 实现 DocNodeVisitor<T> 接口
5. Document 支持 JSON 序列化（使用 Gson 注解，Gson 是 MC 自带）

#### 对外接口
```java
// Document 节点树
DocNode root = new ParagraphNode(List.of(new TextNode("hello")));
String json = new Gson().toJson(root);  // 可序列化
DocNode deserialized = new Gson().fromJson(json, DocNode.class);

// 数据类
var query = new SearchQuery("娜迦");
var hit = new SearchHit("item/123", "娜迦鳞片", "item", "暮色森林");
var page = new ItemPage("item/123", "娜迦鳞片", "暮色森林", document, "https://www.mcmod.cn/item/123.html");
```

---

### Agent B: search 状态管理
**代号**: search-state
**文件边界**:
- `src/main/java/com/cy311/omnisearch/search/` 下所有文件
- `src/test/java/com/cy311/omnisearch/search/` 下测试文件
**禁止触碰**: data.model 包（只引用，不修改）
**依赖**: Agent A 产出的 data.model 类

#### 任务背景
设计文档 05_state_management.md 已定义完整的状态管理和导航方案。

#### 最终规格
1. 实现 SearchState record（不可变状态）
2. 实现 NavigationStack（导航栈）
3. 实现 SearchEvent sealed interface + 所有实现
4. 实现 SearchReducer 纯函数
5. 所有逻辑纯函数，无副作用

#### 对外接口
```java
var state = SearchState.initial();
var event = new SearchEvent.QueryChanged("娜迦");
var nextState = SearchReducer.reduce(state, event);
// nextState.query() → SearchQuery("娜迦")
```

---

## 六、接口契约与接缝定义

### 接口清单

#### data.model 对外接口

```java
// 文件: src/main/java/com/cy311/omnisearch/data/model/document/

public record Document(
    String title,
    @Nullable String sourceMod,
    @Nullable String sourceUrl,
    List<DocNode> content
) {}

public abstract class DocNode {
    public abstract <T> T accept(DocNodeVisitor<T> visitor);
}

// 具体节点类:
public class HeadingNode extends DocNode { ... }     // level, children
public class ParagraphNode extends DocNode { ... }    // children
public class TableNode extends DocNode { ... }        // headers, rows
public class ListNode extends DocNode { ... }         // ordered, items
public class ImageNode extends DocNode { ... }        // url, alt, localPath
public class LinkNode extends DocNode { ... }         // url, children
public class DividerNode extends DocNode { ... }      // 无字段
public class SectionNode extends DocNode { ... }      // title, children

// 行内节点:
public class TextNode extends DocNode { ... }         // text (Leaf)
public class StyledTextNode extends DocNode { ... }   // text, style (Leaf)
public class ImageInlineNode extends DocNode { ... }  // url, alt (Leaf)

// Visitor:
public interface DocNodeVisitor<T> {
    T visitHeading(HeadingNode node);
    T visitParagraph(ParagraphNode node);
    T visitTable(TableNode node);
    T visitList(ListNode node);
    T visitImage(ImageNode node);
    T visitLink(LinkNode node);
    T visitDivider(DividerNode node);
    T visitSection(SectionNode node);
    // 行内节点
    T visitText(TextNode node);
    T visitStyledText(StyledTextNode node);
    T visitImageInline(ImageInlineNode node);
}

// 文件: src/main/java/com/cy311/omnisearch/data/model/
public record SearchQuery(String text) {}
public record SearchHit(String id, String name, String type, String sourceMod) {}
public record ItemPage(String id, String title, String sourceMod, Document document, String url) {}
public record CaptchaContext(String captchaImageUrl, String captchaId) {}
```

#### search 对外接口

```java
// 文件: src/main/java/com/cy311/omnisearch/search/
public record SearchState(
    Page currentPage,
    SearchQuery query,
    List<SearchHit> results,
    @Nullable ItemPage detailPage,
    NavigationStack navStack,
    LoadingState loading,
    @Nullable String errorMessage,
    @Nullable CaptchaContext captcha
) {
    public enum Page { SEARCH, RESULTS, DETAIL }
    public enum LoadingState { IDLE, LOADING, CAPTCHA_REQUIRED, ERROR }
    public static SearchState initial() { ... }
    // with* 便捷方法
}

public sealed interface SearchEvent {
    record QueryChanged(String query) implements SearchEvent {}
    record SearchSubmitted() implements SearchEvent {}
    record ResultSelected(int index) implements SearchEvent {}
    record DetailLoaded(ItemPage page) implements SearchEvent {}
    record LinkClicked(String url) implements SearchEvent {}
    record GoBack() implements SearchEvent {}
    record CaptchaSolved(String solution) implements SearchEvent {}
    record ErrorOccurred(String message) implements SearchEvent {}
    record Dismiss() implements SearchEvent {}
}

public class NavigationStack {
    public SearchState push(SearchState state);
    public SearchState pop();
    public boolean canGoBack();
}

public class SearchReducer {
    public static SearchState reduce(SearchState current, SearchEvent event);
}
```

### 接缝定义

| 接缝位置 | 上游输出 | 下游期望 | 转换责任方 |
|---------|---------|---------|-----------|
| data.model → search | DocNode 节点树 + SearchHit/ItemPage/SearchQuery | SearchState 引用这些类型 | search（直接引用，不转换） |
| search → gui | SearchState record + SearchEvent sealed interface | Screen 读状态、发事件 | gui（Screen） |

---

## 七、进度追踪

| Agent | 状态 | 完成说明 |
|-------|------|---------|
| A: data-model | ✅已完成 | 16 个源文件：DocNode 节点树（11 个子类+Visitor+Adapter）+ 4 个数据类 record。64 测试通过。 |
| B: search-state | ✅已完成 | 4 个源文件：SearchState/SearchEvent/NavigationStack/SearchReducer。89 测试通过。|

---

## 历史记录

### 2026-06-13 第1轮：data.model + search 基石实现
- **结果**：✅ 完成
- **产出**：
  - data.model: DocNode 节点树（11 个子类 + DocNodeVisitor + DocNodeAdapterFactory）
  - data.model: Document/SearchQuery/SearchHit/ItemPage/CaptchaContext 5 个 record
  - search: SearchState record + SearchEvent sealed interface + NavigationStack + SearchReducer
  - 测试: 7 个测试类，共 153 个测试
  - 构建: build.gradle.kts 兼容性修复（Gradle 9.x + NeoForge ModDevGradle）
- **遗留技术债**：无
- **经验教训**：Gradle 9.x 的 NeoForge ModDevGradle 有 API 变更（runs block 的 DSL），需要测试验证构建兼容性
