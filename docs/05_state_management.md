# Omnisearch v2 — 状态管理与导航

## 旧代码的问题

OmnisearchScreen 556行的核心问题：状态是布尔标记（isSearching, showDetail, isLoading...），逻辑是if-else嵌套，加一个状态就要改一堆地方。开发agent可通过 `gh api repos/c3115644151/omnisearch/contents/src/main/java/com/cy311/omnisearch/client/gui/OmnisearchScreen.java?ref=main` 查看旧实现。

## 新方案：不可变数据类 + 纯函数Reducer

Screen只做 state → render，不包含业务逻辑。

## 状态定义

```java
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
}
```

## 导航栈

```java
public class NavigationStack {
    private final Deque<SearchState> stack;

    public SearchState push(SearchState state);  // 进入新页面
    public SearchState pop();                     // 返回上一页
    public boolean canGoBack();
}
```

栈结构：
```
搜索首页 → 搜索结果 → 详情页A → 详情页B（点击链接）
                                          ↑ 当前位置
pop → 详情页A
pop → 搜索结果
pop → 搜索首页
```

## 事件定义

```java
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
```

## Reducer（纯函数）

```java
public class SearchReducer {

    public static SearchState reduce(SearchState current, SearchEvent event) {
        return switch (event) {
            case QueryChanged q -> current.withQuery(new SearchQuery(q.query()));
            case SearchSubmitted -> current.withLoading(LoadingState.LOADING);
            case ResultSelected i -> {
                var hit = current.results().get(i.index());
                yield current.withNavStack(current.navStack().push(current))
                             .withPage(Page.DETAIL)
                             .withLoading(LoadingState.LOADING);
            }
            case DetailLoaded p -> current.withDetailPage(p)
                                          .withLoading(LoadingState.IDLE);
            case GoBack -> current.navStack().pop();
            case CaptchaSolved s -> current.withLoading(LoadingState.LOADING);
            case ErrorOccurred e -> current.withLoading(LoadingState.ERROR)
                                           .withErrorMessage(e.message());
            case Dismiss -> SearchState.initial();  // 重置
        };
    }
}
```

关键特性：
- **纯函数**：相同输入永远产生相同输出，无副作用
- **不可变**：每次事件产生新状态对象，不修改旧状态
- **可测试**：不需要MC环境，构造state+event断言newState即可

## Screen怎么用

```java
public class OmnisearchScreen extends Screen {
    private SearchState state = SearchState.initial();

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        // 只做 state → render
        switch (state.currentPage()) {
            case SEARCH -> renderSearchBar(g);
            case RESULTS -> renderResultList(g);
            case DETAIL -> renderDetailView(g);
        }
        if (state.loading() == LoadingState.CAPTCHA_REQUIRED) {
            renderCaptchaDialog(g);
        }
    }

    private void onEvent(SearchEvent event) {
        state = SearchReducer.reduce(state, event);
        // 副作用（网络请求等）单独处理
        handleSideEffects(event);
    }
}
```

副作用（网络请求、图片加载）不在reducer里，在Screen的事件处理中触发。reducer只管状态转换。
