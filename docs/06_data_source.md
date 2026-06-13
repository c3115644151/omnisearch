# Omnisearch v2 — 数据源层

## 旧代码的问题

McmodFetcher（158行）把HTTP请求、HTML解析、数据提取全写一起，Screen直接调Fetcher，没有缓存没有离线。开发agent可通过 `gh api repos/c3115644151/omnisearch/contents/src/main/java/com/cy311/omnisearch/fetcher/McmodFetcher.java?ref=main` 查看旧实现。

MapleSugar365的fork中有一个带CAPTCHA支持的McmodFetcher重写版本，可通过 `gh api repos/MapleSugar365/omnisearch/contents/src/main/java/com/cy311/omnisearch/fetcher/McmodFetcher.java?ref=hmmcm-hotfix` 查看。**这是已验证可行的CAPTCHA方案，开发时应直接参考。**

## 架构

```
Screen / SearchReducer
        ↓
  SearchRepository（门面）
        ↓
  1. CacheLayer.check(query)
     → hit: 直接返回缓存的Document
     → miss: 继续下一步
        ↓
  2. DataSource.search(query) / getPage(id)
        ↓
  3. McmodHttpClient.request(url)
        ↓
  4. 响应检查：
     → 正常HTML → McmodParser.parse(html) → Document → 缓存 → 返回
     → CAPTCHA页面 → McmodCaptchaHandler处理 → 用户手动解 → 重试请求
     → 网络错误 → 返回缓存（如果有过期缓存）或错误状态
```

## Repository

```java
public class SearchRepository {
    private final CacheLayer cache;
    private final DataSource primarySource;   // mcmod.cn
    private final List<DataSource> fallbackSources;  // 待确认

    public CompletableFuture<List<SearchHit>> search(SearchQuery query) {
        // 1. 先查缓存
        var cached = cache.getSearchResults(query);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        // 2. 缓存miss，请求远程
        return primarySource.search(query)
            .thenApply(results -> {
                cache.putSearchResults(query, results);
                return results;
            })
            .exceptionally(ex -> {
                // 3. 请求失败，尝试过期缓存
                var stale = cache.getSearchResultsStale(query);
                if (stale != null) return stale;
                throw new CompletionException(ex);
            });
    }

    public CompletableFuture<ItemPage> getPage(String pageId) {
        // 同样逻辑：缓存优先，CAPTCHA不阻塞
        var cached = cache.getPage(pageId);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return primarySource.getPage(pageId)
            .thenApply(page -> {
                cache.putPage(pageId, page);
                return page;
            });
    }
}
```

## DataSource接口

```java
public interface DataSource {
    CompletableFuture<List<SearchHit>> search(SearchQuery query);
    CompletableFuture<ItemPage> getPage(String pageId);
    String name();
    boolean isAvailable();  // 检测数据源是否可用
}
```

可插拔设计：新增数据源只需实现此接口，注册到Repository即可。

## McmodDataSource

```java
public class McmodDataSource implements DataSource {
    private final McmodHttpClient client;
    private final McmodParser parser;
    private final McmodCaptchaHandler captchaHandler;

    @Override
    public CompletableFuture<List<SearchHit>> search(SearchQuery query) {
        return client.search(query.text())
            .thenCompose(response -> {
                if (captchaHandler.isCaptchaPage(response)) {
                    // CAPTCHA处理：通知状态层，等待用户解决
                    return captchaHandler.handleCaptcha(response)
                        .thenCompose(captchaResponse -> client.search(query.text()));
                }
                return CompletableFuture.completedFuture(response);
            })
            .thenApply(parser::parseSearchResults);
    }
}
```

## CAPTCHA处理

mcmod.cn因遭受网络攻击已升级安全策略，CAPTCHA是预期常态而非异常。MapleSugar365的fork已实现并验证了CAPTCHA处理方案，开发时应直接参考其实现：

- **Fork仓库**：https://github.com/MapleSugar365/omnisearch
- **分支**：hmmcm-hotfix
- **关键文件**：CaptchaInfo.java、McmodFetcher.java（验证码版）、OmnisearchScreen.java（验证码UI版）

设计要求：
1. **CAPTCHA检测**：McmodHttpClient检测响应中的验证码特征
2. **不阻塞界面**：先显示缓存数据（如果有的话），后台处理验证码
3. **用户手动解**：弹出CAPTCHA对话框，用户输入验证码后继续
4. **自动重试**：验证码解决后自动重试被拦截的请求

```java
public class McmodCaptchaHandler {
    // 检测是否为验证码页面
    public boolean isCaptchaPage(HttpResponse response);

    // 提取验证码图片URL
    public String extractCaptchaImageUrl(HttpResponse response);

    // 提交验证码解决方案
    public CompletableFuture<HttpResponse> submitCaptcha(String solution);
}
```

## CacheLayer

```java
public class CacheLayer {
    private final Path cacheDir;  // 游戏目录下的缓存文件夹

    // 搜索结果缓存
    public List<SearchHit> getSearchResults(SearchQuery query);
    public void putSearchResults(SearchQuery query, List<SearchHit> results);

    // 页面缓存（Document序列化，非原始HTML）
    public ItemPage getPage(String pageId);
    public void putPage(String pageId, ItemPage page);

    // 过期缓存（网络错误时的降级）
    public List<SearchHit> getSearchResultsStale(SearchQuery query);
    public ItemPage getPageStale(String pageId);

    // 缓存策略
    // - 搜索结果：7天过期
    // - 页面详情：30天过期
    // - 过期缓存保留90天（仅错误降级时使用）
}
```

缓存存储Document的JSON序列化，而非原始HTML。原因见03_data_model.md。

## mcmod.cn交互细节

### 搜索请求

```
GET https://www.mcmod.cn/s?key={query}&page=1
```

### 物品详情页

```
GET https://www.mcmod.cn/item/{id}.html
```

### 模组详情页

```
GET https://www.mcmod.cn/mod/{id}.html
```

⚠️ **待验证**：以上URL模式是否仍然有效。mcmod.cn已升级安全策略，2026-06-13实测fetch返回 `err_code:7, "link hit security strategy"`。但MapleSugar365的fork证明通过正确的请求方式（带Cookie、Referer等）仍可正常访问。开发时需参考fork的HTTP请求实现。

## 待确认问题

1. **是否需要补充数据源？** CurseForge/Modrinth有公开API，可以补全英文mod信息，但Omnisearch的核心价值是mcmod.cn的中文内容，需要项目所有者确认是否有必要
2. **CAPTCHA的触发频率？** 决定缓存策略和用户体验。需要实际使用中观察
3. **mcmod.cn是否有未公开的JSON API？** 如果有，可以绕过HTML解析，大幅降低维护成本
