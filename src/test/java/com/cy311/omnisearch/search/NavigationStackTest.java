package com.cy311.omnisearch.search;

import com.cy311.omnisearch.data.model.SearchQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationStackTest {

    @Test
    void newStack_cannotGoBack() {
        var stack = new NavigationStack();
        assertFalse(stack.canGoBack());
    }

    @Test
    void push_then_canGoBack() {
        var stack = new NavigationStack();
        var pushed = stack.push(SearchState.initial());
        assertTrue(pushed.canGoBack());
        // Original stack is unchanged
        assertFalse(stack.canGoBack());
    }

    @Test
    void pushAndPop_returnsSameState() {
        var state = SearchState.initial();
        var pushed = new NavigationStack().push(state);
        var result = pushed.pop();
        assertSame(state, result.state());
        assertFalse(result.newStack().canGoBack());
    }

    @Test
    void pushAndPop_preservesStateContent() {
        var state = SearchState.initial()
            .withQuery(new SearchQuery("娜迦"))
            .withPage(SearchState.Page.RESULTS);
        var pushed = new NavigationStack().push(state);
        var result = pushed.pop();
        assertEquals("娜迦", result.state().query().text());
        assertEquals(SearchState.Page.RESULTS, result.state().currentPage());
    }

    @Test
    void popOnEmptyStack_returnsNullState() {
        var stack = new NavigationStack();
        var result = stack.pop();
        assertNull(result.state());
        assertSame(stack, result.newStack());
    }

    @Test
    void popOnEmptyStack_cannotGoBackAfter() {
        var stack = new NavigationStack();
        var result = stack.pop();
        assertNull(result.state());
        assertFalse(result.newStack().canGoBack());
    }

    @Test
    void multiLevelPushPop() {
        var s1 = SearchState.initial();
        var s2 = s1.withQuery(new SearchQuery("第一页"));
        var s3 = s2.withQuery(new SearchQuery("第二页"));

        var stack = new NavigationStack()
            .push(s1)
            .push(s2);
        assertTrue(stack.canGoBack());

        // Pop should get s2 back
        var p1 = stack.pop();
        assertSame(s2, p1.state());
        assertTrue(p1.newStack().canGoBack());

        // Second pop should get s1 back
        var p2 = p1.newStack().pop();
        assertSame(s1, p2.state());
        assertFalse(p2.newStack().canGoBack());

        // Third pop on empty stack should return null state
        var p3 = p2.newStack().pop();
        assertNull(p3.state());
    }

    @Test
    void pushReturnsNewInstance() {
        var original = new NavigationStack();
        var pushed = original.push(SearchState.initial());
        assertNotSame(original, pushed);
    }

    @Test
    void canGoBack_afterMultiplePops() {
        var stack = new NavigationStack()
            .push(SearchState.initial())
            .push(SearchState.initial().withQuery(new SearchQuery("test")));

        var p1 = stack.pop();
        assertTrue(p1.newStack().canGoBack());

        var p2 = p1.newStack().pop();
        assertFalse(p2.newStack().canGoBack());
    }

    @Test
    void pushAfterPop_maintainsCorrectOrder() {
        var s1 = SearchState.initial().withQuery(new SearchQuery("s1"));
        var s2 = SearchState.initial().withQuery(new SearchQuery("s2"));
        var s3 = SearchState.initial().withQuery(new SearchQuery("s3"));

        // Push s1 → push s2 → pop s2 → push s3 → push s2
        var stack = new NavigationStack()
            .push(s1)
            .push(s2);
        var afterPop = stack.pop().newStack(); // pops s2
        var afterPushS3 = afterPop.push(s3);
        var finalStack = afterPushS3.push(s2);

        // Order: [s1, s3, s2] (s2 on top)
        var r1 = finalStack.pop();
        assertSame(s2, r1.state());
        var r2 = r1.newStack().pop();
        assertSame(s3, r2.state());
        var r3 = r2.newStack().pop();
        assertSame(s1, r3.state());
        assertFalse(r3.newStack().canGoBack());
    }

    @Test
    void multipleStacks_areIndependent() {
        var stack1 = new NavigationStack();
        var stack2 = new NavigationStack();

        var pushed1 = stack1.push(SearchState.initial());
        assertTrue(pushed1.canGoBack());
        assertFalse(stack2.canGoBack());

        var pushed2 = stack2.push(SearchState.initial());
        assertTrue(pushed2.canGoBack());
        assertTrue(pushed1.canGoBack());
        // Originals still unchanged
        assertFalse(stack1.canGoBack());
        assertFalse(stack2.canGoBack());
    }

    @Test
    void push_withModifiedState() {
        var state = SearchState.initial()
            .withQuery(new SearchQuery("test"))
            .withPage(SearchState.Page.RESULTS);
        var pushed = new NavigationStack().push(state);
        var popped = pushed.pop();
        assertEquals(state.currentPage(), popped.state().currentPage());
        assertEquals(state.query(), popped.state().query());
    }
}
