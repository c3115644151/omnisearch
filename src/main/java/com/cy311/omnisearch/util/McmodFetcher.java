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


        Document searchPage = Jsoup.connect(searchUrl).get();



        // 否则，从搜索结果列表中找到所有匹配的链接
        Elements results = searchPage.select(".search-result-list .result-item .head a[href*='/item/']");

        if (results.isEmpty()) {
            return null; // 没有找到结果
        }

        // 区分精确匹配和广泛匹配
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


        // 合并列表，精确匹配的在前
        List<Element> combinedResults = new ArrayList<>(exactMatches);
        combinedResults.addAll(broadMatches);

        if (combinedResults.isEmpty()) {
            return null; // 理论上不会发生，因为上面已经判断过 results.isEmpty()
        }

        if (combinedResults.size() == 1) {
            ItemData itemData = parseItemDetails(combinedResults.get(0).absUrl("href"));
            return FetchResult.withItemData(itemData);
        }

        // 有多个结果，返回一个 SearchResult 列表
        List<SearchResult> searchResults = combinedResults.stream()
                .map(element -> {
                    String linkText = element.text();
                    String resultItemName = extractItemName(linkText);
                    String modName = extractModName(linkText);
                    String url = element.absUrl("href");
                    return new SearchResult(resultItemName, modName, url);
                })
                .collect(Collectors.toList());
        return FetchResult.withSearchResults(searchResults);
    }

    private static String extractItemName(String linkText) {
        // 披萨 (Pizza) - 甜蜜与魔法 (SweetMagic)
        // 披萨
        // (Pizza)
        String name = linkText.split("\\(")[0].trim();
        if (name.contains(" - ")) {
            name = name.substring(0, name.indexOf(" - ")).trim();
        }
        return name;
    }

    private static String extractModName(String linkText) {
        if (linkText.contains(" - ")) {
            String modPart = linkText.substring(linkText.indexOf(" - ") + 3).trim();
            if (modPart.startsWith("(")) {
                modPart = modPart.substring(1);
            }
            if (modPart.endsWith(")")) {
                modPart = modPart.substring(0, modPart.length() - 1);
            }
            return modPart;
        }
        return "vanilla_or_unknown";
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