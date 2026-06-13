package com.cy311.omnisearch.search;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import java.util.List;

public sealed interface SearchEvent {
    record QueryChanged(String query) implements SearchEvent {}
    record SearchSubmitted() implements SearchEvent {}
    record SearchResultsLoaded(List<SearchHit> results) implements SearchEvent {}
    record ResultSelected(int index) implements SearchEvent {}
    record DetailLoaded(ItemPage page) implements SearchEvent {}
    record LinkClicked(String url) implements SearchEvent {}
    record GoBack() implements SearchEvent {}
    record CaptchaSolved(String solution) implements SearchEvent {}
    record ErrorOccurred(String message) implements SearchEvent {}
    record Dismiss() implements SearchEvent {}
}
