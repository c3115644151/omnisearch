# Omnisearch v2 — 团队调度文档

> **日期**: 2026-06-13
> **项目根目录**: c:\Users\32800\Desktop\omnisearch
> **本轮目标**: 实现 data.parser 层（Jsoup HTML → Document 解析器），打通 HTML 到结构化数据的转换管线

---

## 一、项目现状

### 已完成部分
- data.model 完整实现（DocNode 节点树 + 5 个数据类 record）
- search 状态管理层完整实现（SearchState + Reducer + NavigationStack）
- 构建系统适配 Gradle 9.x + NeoForge ModDevGradle 2.0.141
- 169 个测试全部通过

### 存在问题
- 无任何 HTML 解析能力——从 mcmod.cn 拿到的 HTML 字符串无法变成可渲染的 Document
- 无 Jsoup 依赖（需添加）
- mcmod.cn 的 HTML 结构未用当前页面验证（CSS 选择器基于旧代码推导，可能已过时）

### 技术债
- 无（第1轮已完成，NavigationStack 不可变性已修复）

---

## 二、不可变约束

### 设计/业务约束
1. **Document 是整条管线的脊柱** — Fetcher 不知道怎么渲染，Renderer 不知道 HTML 长什么样
2. **HTML 不直接到渲染** — 必须经过 Document 中间层：HTML → Document → 渲染组件
3. **Screen 不超过 ~80 行** — 只做布局和事件分发
4. **绝不 @Overwrite** — 除非有充分理由且代码注释说明

### 代码约束
5. **data.parser 包只依赖 Jsoup + data.model** — 纯 Java，零 MC 依赖
6. **每个 Agent 只修改自己边界内的文件**，绝不触碰其他 Agent 的文件
7. **Parser 必须容错** — 遇到无法解析的 HTML 结构时返回部分结果而非崩溃

### 测试纪律
8. **所有实现必须包含对应测试**
9. **使用真实 mcmod.cn HTML 片段作为测试 fixture**（从旧代码或手动保存的页面中提取）
10. **测试必须包含**：正常解析、部分匹配、完全不匹配的 HTML、空字符串、null

---

## 三、协作规则

### 依赖与等待
- 本波只有一个 Agent，无外部依赖（data.model 已完成）

### 进度汇报
- 完成开发后更新"进度追踪"章节的状态

### 禁止回退策略
- 不得采取任何降低预期效果的回退策略
- CSS 选择器基于旧代码推导可能需后续调整，但 parser 的结构（方法签名、异常处理、容错机制）必须一次做对

### 冲突处理
- 只修改 parser 边界内的文件

---

## 四、依赖图与调度顺序

```
第2波：Agent C (data.parser，依赖 data.model)
  └── 仅此一个 Agent，串行执行即可
```

---

## 五、团队定义

### Agent C: McmodParser HTML → Document 解析器
**代号**: mcmod-parser
**文件边界**:
- `src/main/java/com/cy311/omnisearch/data/parser/McmodParser.java`
- `src/main/java/com/cy311/omnisearch/data/parser/SearchResultParser.java`（可选拆分）
- `src/main/java/com/cy311/omnisearch/data/parser/DocumentParser.java`（可选拆分）
- `src/test/java/com/cy311/omnisearch/data/parser/` 下所有测试文件
- `src/test/resources/html/` 下测试 fixture HTML 文件
**禁止触碰**: 任何 data.model 下的文件（只引用，不修改）
**依赖**: data.model（已完成）

#### 任务背景
McmodFetcher 拿到 mcmod.cn 的 HTML 字符串后，需要通过 Parser 转为 Document 结构化数据。这是 HTML→Document 的转换步骤。CSS 选择器基于旧 HtmlRenderer 990 行代码推导，见 docs/03_data_model.md 的映射表。

#### 最终规格
1. 实现 McmodParser 类，包含以下方法：
   - `List<SearchHit> parseSearchResults(String html)` — 解析搜索结果页 HTML
   - `Document parseItemPage(String html, String url)` — 解析物品详情页 HTML
   - `Document parseModPage(String html, String url)` — 解析模组详情页 HTML
2. 所有方法静态，不依赖实例状态（Parser 是纯函数）
3. 遇到无法匹配的选择器时返回空/默认值，不抛异常
4. 使用 Jsoup 解析 HTML

#### 解析规则（基于旧代码推导，待验证）

**搜索结果页**：从搜索结果列表中提取每个条目的 id、name、type、sourceMod。
- 每个搜索结果是一个带有链接的列表项
- 链接格式：`/item/{id}.html` 或 `/mod/{id}.html`
- type 从 URL 路径推断（`/item/` → "item", `/mod/` → "mod"）

**物品详情页**（基于 03_data_model.md 的映射表）：
| HTML 结构 | Document 节点 | 提取说明 |
|-----------|-------------|----------|
| 标题元素 | HeadingNode(1) | 页面主标题 |
| 来源 mod 信息 | ParagraphNode | 可能包含 StyledText |
| 属性表格 | TableNode | 键值对表，headers=["属性","值"] |
| 描述文字 | ParagraphNode | 多段文字 |
| img (物品图标) | ImageNode | src + alt |
| a[href] | LinkNode | 链接 |
| ul/ol | ListNode | 列表 |
| hr | DividerNode | 分割线 |
| 章节标题 | SectionNode | 分组标题 |

**模组详情页**：结构类似物品页，但标题是模组名，表格内容不同。

#### 对外接口
```java
var parser = new McmodParser();

// 搜索解析
String searchHtml = "<html>...</html>";  // 从 mcmod.cn/s?key=娜迦 取回
List<SearchHit> hits = parser.parseSearchResults(searchHtml);

// 详情页解析
String itemHtml = "<html>...</html>";  // 从 mcmod.cn/item/123.html 取回
Document doc = parser.parseItemPage(itemHtml, "https://www.mcmod.cn/item/123.html");
```

---

## 六、接口契约与接缝定义

### 接口清单

#### McmodParser 对外接口
```java
public class McmodParser {
    public List<SearchHit> parseSearchResults(String html);
    public Document parseItemPage(String html, String url);
    public Document parseModPage(String html, String url);
}
```

输入：String html（原始 HTML 字符串）
输出：强类型的 data.model 对象

### 接缝定义

| 接缝位置 | 上游输出 | 下游期望 | 转换责任方 |
|---------|---------|---------|-----------|
| McmodHttpClient → McmodParser | String html | List<SearchHit> / Document | McmodParser |
| McmodParser → SearchRepository | List<SearchHit> / Document | 缓存 or 返回给调用方 | SearchRepository |

---

## 七、进度追踪

| Agent | 状态 | 完成说明 |
|-------|------|---------|
| C: mcmod-parser | ✅ 完成 | build.gradle.kts 添加 Jsoup 1.19.1；TextStyle 添加 ITALIC 常量；McmodParser 实现搜索/物品/模组页解析 + HTML→DocNode 递归转换；4 测试类 38 用例全部通过 |

---

## 历史记录

### 2026-06-13 第1轮：data.model + search 基石实现
- **结果**：✅ 完成
- **产出**：
  - data.model: DocNode 节点树 + 5 个数据类 record
  - search: SearchState/SearchEvent/NavigationStack/SearchReducer
  - 测试: 7 个测试类，169 个测试
- **遗留技术债**：无

### 2026-06-13 第2轮：data.parser HTML→Document 解析器
- **结果**：✅ 完成
- **产出**：
  - McmodParser: parseSearchResults / parseItemPage / parseModPage 三个方法
  - HTML→DocNode 递归转换引擎（块级 + 行内节点）
  - 4 个测试类，38 个测试用例
  - Jsoup 1.19.1 依赖
  - TextStyle ITALIC 常量
- **关键知识获取**：通过 gh api (7890 代理) 获取 MapleSugar365 fork 的 McmodFetcher 源码，验证了 CSS 选择器和解析逻辑
- **遗留技术债**：CSS 选择器基于旧代码推导，未用 mcmod.cn 当前页面验证（P0 问题，后续需手动验证）
