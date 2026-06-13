# Omnisearch v2 — 团队调度文档

> **日期**: 2026-06-13
> **项目根目录**: c:\Users\32800\Desktop\omnisearch
> **本轮目标**: 实现 CacheLayer（文件缓存）+ SearchRepository（编排层），完成纯 Java 数据层管线

---

## 一、项目现状

### 已完成部分
- 完整数据管线：HttpClient → Parser → DataSource
- data.model + search 状态管理 + data.parser + data.client + data.source
- 271 测试，全部通过

### 存在问题
- 无缓存——每次搜索/查看都发 HTTP 请求
- 无编排层——Screen 需要直接拼装 HttpClient + Parser
- 离线无降级——网络错误时没有任何后备数据

### 技术债
- CSS 选择器基于旧代码推导，待用当前 mcmod.cn 页面验证

---

## 二、不可变约束

### 设计约束
1. **缓存优先** — 先查缓存再发请求，网络错误时用过期缓存降级
2. **纯 Java 无 MC 依赖** — 文件 I/O 使用 java.nio.file
3. **Repository 是纯 Java 层的出口** — 之上是 MC 渲染层

### 测试纪律
4. **所有实现包含测试**：临时目录测试文件 I/O、Gson 序列化、TTL 过期

---

## 三、依赖图

```
无依赖（DataLayerAgent F 一个 Agent 完成两个模块）

Agent F: CacheLayer + SearchRepository
  └── 依赖: CacheLayer → data.model (Gson 序列化)
  └── 依赖: SearchRepository → CacheLayer + DataSource + SearchState
```

---

## 四、团队定义

### Agent F: CacheLayer + SearchRepository
**代号**: data-repository
**文件边界**:
- `src/main/java/com/cy311/omnisearch/data/repository/CacheLayer.java`
- `src/main/java/com/cy311/omnisearch/data/repository/SearchRepository.java`
- `src/test/java/com/cy311/omnisearch/data/repository/` 下所有文件
**禁止触碰**: 任何已有文件（只引用 data.model / data.source / search）
**依赖**: data.model + data.source + search（全部已完成）

#### CacheLayer

文件级缓存，使用 Gson JSON 序列化存储 Document 和 SearchHit 列表。

```java
public class CacheLayer {
    private final Path cacheDir;
    private final Gson gson;

    public CacheLayer(Path cacheDir);

    // 搜索结果缓存 (7 天 TTL)
    public @Nullable List<SearchHit> getSearchResults(SearchQuery query);
    public void putSearchResults(SearchQuery query, List<SearchHit> results);
    public @Nullable List<SearchHit> getSearchResultsStale(SearchQuery query);

    // 页面缓存 (30 天 TTL, 过期缓存 90 天)
    public @Nullable ItemPage getPage(String pageId);
    public void putPage(String pageId, ItemPage page);
    public @Nullable ItemPage getPageStale(String pageId);

    public void clear();
}
```

缓存目录结构：
```
{cacheDir}/
  search/{md5(query)}.json     7天 TTL
  page/{pageId}.json           30天 TTL
  stale/search/{md5(query)}.json   90天
  stale/page/{pageId}.json         90天
```

每个缓存文件包含数据和时间戳：
```java
record CacheEntry<T>(T data, long timestamp) {}
```

Gson 配置使用已有的 DocNodeAdapterFactory。

#### SearchRepository

```java
public class SearchRepository {
    private final CacheLayer cache;
    private final DataSource primarySource;

    public SearchRepository(CacheLayer cache, DataSource primarySource);

    public CompletableFuture<List<SearchHit>> search(SearchQuery query);
    public CompletableFuture<ItemPage> getPage(String pageId);
}
```

`search`: 查缓存 → hit 直接返回 → miss 调 DataSource → 缓存结果 → 返回。异常时尝试过期缓存。

`getPage`: 同 search 逻辑。

---

## 五、进度追踪

| Agent | 状态 | 完成说明 |
|-------|------|---------|
| F: data-repository | ✅已完成 | CacheLayer（文件缓存+Gson+TTL）+ SearchRepository（缓存优先编排）+ 16 测试 |

---

## 历史记录

### 2026-06-13 第1轮：data.model + search 基石（169 测试）
### 2026-06-13 第2轮：data.parser HTML→Document 解析器（221 测试）
### 2026-06-13 第3a轮：data.client + data.source（271 测试）

### 2026-06-13 第3b轮：CacheLayer + SearchRepository
- **结果**：✅ 完成
- **产出**：
  - CacheLayer: 文件缓存 + Gson 序列化 + TTL(7天/30天/90天) + stale 降级
  - SearchRepository: 缓存优先编排 + 网络异常过期缓存降级
  - CacheEntry 泛型 record
  - 新增 16 测试（repository 层）
  - **总测试数 287，全部通过**
