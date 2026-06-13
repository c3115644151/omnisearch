package com.cy311.omnisearch.search;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable navigation stack. push() and pop() return new NavigationStack instances
 * without modifying the original. This ensures SearchReducer remains a pure function.
 */
public class NavigationStack {
    private final List<SearchState> entries;

    public NavigationStack() {
        this.entries = List.of();
    }

    private NavigationStack(List<SearchState> entries) {
        this.entries = entries;
    }

    /**
     * Returns a new NavigationStack with the given state pushed on top.
     * The original stack is not modified.
     */
    public NavigationStack push(SearchState state) {
        var newEntries = new ArrayList<SearchState>(entries.size() + 1);
        newEntries.addAll(entries);
        newEntries.add(state);
        return new NavigationStack(Collections.unmodifiableList(newEntries));
    }

    /**
     * Returns a PopResult containing the popped state and the new stack.
     * If the stack is empty, state is null and the returned newStack is {@code this}.
     */
    public PopResult pop() {
        if (entries.isEmpty()) {
            return new PopResult(null, this);
        }
        var popped = entries.get(entries.size() - 1);
        var remaining = entries.subList(0, entries.size() - 1);
        return new PopResult(popped, new NavigationStack(List.copyOf(remaining)));
    }

    public boolean canGoBack() {
        return !entries.isEmpty();
    }

    /**
     * Result of a pop() operation. Contains the popped state (or null if empty)
     * and the new NavigationStack after removal.
     */
    public record PopResult(@Nullable SearchState state, NavigationStack newStack) {}
}
