# Omnisearch v2 — 文档对象模型

## 设计目标

旧代码的核心问题：McmodFetcher拿到HTML字符串后，直接传给990行的HtmlRenderer，里面解析、布局、渲染全混在一起。没有中间数据模型意味着：

- 缓存只能存原始HTML（体积大、结构不可控）
- UI绑定HTML结构（mcmod.cn改版就崩）
- 测试只能在MC渲染环境里跑

Document是HTML和渲染之间的中间表示。有了这层：

- **Parser**只需要关心"HTML里有什么结构"，不需要知道怎么画
- **Renderer**只需要关心"Document节点怎么变成像素"，不需要知道HTML长什么样
- **Cache**可以存Document序列化结果而非原始HTML（体积更小、结构可控）
- **测试**可以脱离MC环境，构造Document做单元测试

## 节点类型体系

```
DocNode（抽象基类）
├── HeadingNode(level: int, children: List<InlineNode>)
├── ParagraphNode(children: List<InlineNode>)
├── TableNode(headers: List<String>, rows: List<List<DocNode>>)
├── ListNode(ordered: boolean, items: List<DocNode>)
├── ImageNode(url: String, alt: String, @Nullable localPath: String)
├── LinkNode(url: String, children: List<InlineNode>)
├── DividerNode
└── SectionNode(title: String, children: List<DocNode>)

InlineNode（行内容器）
├── TextNode(text: String)
├── StyledTextNode(text: String, style: TextStyle)
├── ImageInlineNode(url: String, alt: String)    // 行内小图标
└── LinkInlineNode(url: String, children: List<InlineNode>)
```

## 设计决策

### 为什么区分DocNode和InlineNode？

MC的渲染是逐行排版的。块级元素（段落、表格、图片）占整行，行内元素（文字、小图标、链接）在同一行内流动。这个区分直接对应布局引擎的排版逻辑——块级元素之间换行，行内元素之间不换行。

### 为什么TableNode是扁平结构而非嵌套DocNode？

mcmod.cn的表格是属性表（键值对），不是复杂嵌套表格。扁平的headers+rows结构简单直接，覆盖实际需求。如果将来遇到复杂表格，可以扩展TableCellNode。

### 为什么ImageNode有localPath？

图片需要异步下载到本地后才能渲染为MC纹理。Parser只填url，ImageManager下载后回填localPath。渲染时检查localPath是否非空决定是否展示图片。

## Document结构

```java
public record Document(
    String title,                    // 页面标题
    String sourceMod,                // 来源mod名
    String sourceUrl,                // 原始URL
    List<DocNode> content            // 正文节点列表
) {}
```

## mcmod.cn HTML → Document 映射

基于旧HtmlRenderer 990行中的HTML结构知识，mcmod.cn物品页面的典型结构：

| HTML结构 | Document节点 | 提取规则 |
|----------|-------------|----------|
| `.item-name` / `h1` | HeadingNode(1) | 标题 |
| `.item-mod` | ParagraphNode + StyledText | 来源mod |
| `.item-info-table` | TableNode | 属性键值对表 |
| `.item-desc` | ParagraphNode | 描述文字 |
| `img` (物品图标) | ImageNode | src + alt |
| `a[href]` | LinkInlineNode | href + 文本 |
| `ul / ol` | ListNode | 列表 |
| `hr` | DividerNode | 分割线 |
| `.section-title` | SectionNode | 章节标题 |

⚠️ **待验证**：上述CSS选择器和HTML结构是基于旧代码推导的，mcmod.cn可能已改版。当前被安全策略拦截无法直接分析页面源码。开发前必须用浏览器手动验证。这是09_open_questions.md中的P0阻塞问题。

## Visitor模式

```java
public interface DocNodeVisitor<T> {
    T visitHeading(HeadingNode node);
    T visitParagraph(ParagraphNode node);
    T visitTable(TableNode node);
    T visitList(ListNode node);
    T visitImage(ImageNode node);
    T visitLink(LinkNode node);
    T visitDivider(DividerNode node);
    T visitSection(SectionNode node);
}
```

Parser产出Document，LayoutEngine和Renderer各自实现Visitor来遍历节点树。新增节点类型只需扩展Visitor接口。

## 序列化（缓存用）

Document序列化为JSON存储，而非缓存原始HTML。原因：

1. 体积更小（剔除CSS/JS/广告/导航等无关HTML）
2. 结构可控（HTML改版不影响已缓存数据）
3. 反序列化直接得到可渲染的对象，跳过Parser

```java
// 伪代码
Document doc = parser.parse(html);
String json = objectMapper.writeValueAsString(doc);  // 缓存写入
Document doc2 = objectMapper.readValue(json, Document.class);  // 缓存读取
```
