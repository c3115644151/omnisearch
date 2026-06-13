package com.cy311.omnisearch.util;

import com.cy311.omnisearch.data.FetchResult;
import com.cy311.omnisearch.data.ItemData;
import com.cy311.omnisearch.data.SearchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class McmodFetcher {

    private static final String SEARCH_URL_TEMPLATE = "https://search.mcmod.cn/s?key=%s&filter=0";

    public static CompletableFuture<FetchResult> fetchItemData(String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return findItemPageUrl(itemName);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public static CompletableFuture<ItemData> fetchItemDetails(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseItemDetails(url);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private static FetchResult findItemPageUrl(String itemName) throws IOException {
        String encodedItemName = URLEncoder.encode(itemName, StandardCharsets.UTF_8);
        String searchUrl = String.format(SEARCH_URL_TEMPLATE, encodedItemName);
        Document searchPage = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .timeout(5000)
                .get();
        Elements results = searchPage.select(".search-result-list .result-item .head a[href*='/item/']");
        List<Element> links = new ArrayList<>(results);
        if (links.isEmpty()) return null;
        List<Element> combined = new ArrayList<>();
        for (Element result : links) {
            String resultName = extractItemName(result.text());
            if (itemName.equalsIgnoreCase(resultName)) combined.add(result);
        }
        for (Element result : links) {
            String resultName = extractItemName(result.text());
            if (!itemName.equalsIgnoreCase(resultName)) combined.add(result);
        }
        List<SearchResult> candidates = combined.stream().map(McmodFetcher::toSearchResult).collect(Collectors.toList());
        return FetchResult.withSearchResults(candidates);
    }

    private static com.cy311.omnisearch.data.SearchResult toSearchResult(Element element) {
        String linkText = element.text();
        String resultItemName = extractItemName(linkText);
        String modName = extractModName(linkText);
        String url = element.absUrl("href");
        return new com.cy311.omnisearch.data.SearchResult(resultItemName, modName, url);
    }

    static String extractItemName(String linkText) {
        String name = linkText.split("\\(")[0].trim();
        if (name.contains(" - ")) {
            name = name.substring(0, name.indexOf(" - ")).trim();
        }
        return name;
    }

    static String extractModName(String linkText) {
        if (!linkText.contains(" - ")) return "vanilla_or_unknown";
        String modPart = linkText.substring(linkText.indexOf(" - ") + 3).trim();
        boolean wrapped = modPart.startsWith("(") && modPart.endsWith(")");
        if (wrapped && modPart.length() >= 2) return modPart.substring(1, modPart.length() - 1);
        return modPart;
    }

    private static com.cy311.omnisearch.data.ItemData parseItemDetails(String itemUrl) throws IOException {
        Document itemPage = Jsoup.connect(itemUrl).get();
        Element titleElement = itemPage.selectFirst("div.itemname h5");
        String title = (titleElement != null) ? titleElement.text() : "标题未找到";
        Elements modLinks = itemPage.select(".common-nav a.item[href*='/class/']");
        String modName = modLinks.isEmpty() ? "未知" : modLinks.last().text();
        Element descriptionContainer = itemPage.selectFirst(".item-content.common-text.font14");
        String htmlContent;
        if (descriptionContainer != null) {
            descriptionContainer.select(".common-text-menu").remove();
            descriptionContainer.select(".uknowtoomuch").remove();
            htmlContent = descriptionContainer.html();
        } else {
            htmlContent = "<p>暂无详细介绍。</p>";
        }
        return new com.cy311.omnisearch.data.ItemData(title, modName, itemUrl, htmlContent);
    }
}