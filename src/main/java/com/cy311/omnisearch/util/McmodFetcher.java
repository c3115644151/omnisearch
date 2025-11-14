package com.cy311.omnisearch.util;

import com.cy311.omnisearch.data.FetchResult;
import com.cy311.omnisearch.data.ItemData;
import com.cy311.omnisearch.data.SearchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class McmodFetcher {

    private static final String SEARCH_URL_TEMPLATE = "https://search.mcmod.cn/s?key=%s&filter=0";
    private static final String BASE_URL = "https://www.mcmod.cn";



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

        Document searchPage = fetchSearchPage(searchUrl);
        List<Element> links = queryItemLinks(searchPage);
        if (links.isEmpty()) {
            return null;
        }
        List<Element> ordered = prioritizeResults(itemName, links);
        List<SearchResult> candidates = ordered.stream()
                .map(McmodFetcher::toSearchResult)
                .collect(Collectors.toList());
        return FetchResult.withSearchResults(candidates);
    }

    private static Document fetchSearchPage(String searchUrl) throws IOException {
        return Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .timeout(5000)
                .get();
    }

    private static List<Element> queryItemLinks(Document searchPage) {
        Elements results = searchPage.select(".search-result-list .result-item .head a[href*='/item/']");
        return new ArrayList<>(results);
    }

    private static List<Element> prioritizeResults(String itemName, List<Element> results) {
        List<Element> exactMatches = new ArrayList<>();
        List<Element> broadMatches = new ArrayList<>();
        for (Element result : results) {
            String resultName = extractItemName(result.text());
            if (itemName.equalsIgnoreCase(resultName)) {
                exactMatches.add(result);
            } else {
                broadMatches.add(result);
            }
        }
        List<Element> combined = new ArrayList<>(exactMatches);
        combined.addAll(broadMatches);
        return combined;
    }

    private static SearchResult toSearchResult(Element element) {
        String linkText = element.text();
        String resultItemName = extractItemName(linkText);
        String modName = extractModName(linkText);
        String url = element.absUrl("href");
        return new SearchResult(resultItemName, modName, url);
    }

    static String extractItemName(String linkText) {
        // 披萨 (Pizza) - 甜蜜与魔法 (SweetMagic)
        // 披萨
        // (Pizza)
        String name = linkText.split("\\(")[0].trim();
        if (name.contains(" - ")) {
            name = name.substring(0, name.indexOf(" - ")).trim();
        }
        return name;
    }

    static String extractModName(String linkText) {
        if (!linkText.contains(" - ")) {
            return "vanilla_or_unknown";
        }
        String modPart = linkText.substring(linkText.indexOf(" - ") + 3).trim();
        boolean wrapped = modPart.startsWith("(") && modPart.endsWith(")");
        if (wrapped && modPart.length() >= 2) {
            return modPart.substring(1, modPart.length() - 1);
        }
        return modPart;
    }

    private static ItemData parseItemDetails(String itemUrl) throws IOException {
        Document itemPage = Jsoup.connect(itemUrl).get();

        // 1. 提取标题
        Element titleElement = itemPage.selectFirst("div.itemname h5");
        String title = (titleElement != null) ? titleElement.text() : "标题未找到";

        // 2. 提取所属模组
        Elements modLinks = itemPage.select(".common-nav a.item[href*='/class/']");
        String modName = modLinks.isEmpty() ? "未知" : modLinks.last().text();

        // 3. 提取HTML内容
        Element descriptionContainer = itemPage.selectFirst(".item-content.common-text.font14");
        String htmlContent;
        if (descriptionContainer != null) {
            // 移除不希望在游戏内显示的部分
            descriptionContainer.select(".common-text-menu").remove(); // 目录
            descriptionContainer.select(".uknowtoomuch").remove(); // 用户吐槽

            htmlContent = descriptionContainer.html();
        } else {
            htmlContent = "<p>暂无详细介绍。</p>";
        }

        return new ItemData(title, modName, itemUrl, htmlContent);
    }
}