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

    private static boolean hasJsoup() {
        try {
            Class.forName("org.jsoup.Jsoup");
            Class.forName("org.jsoup.nodes.Document");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

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
        System.out.println("[Omnisearch] search URL: " + searchUrl);
        if (hasJsoup()) {
            Document searchPage = fetchSearchPage(searchUrl);
            List<Element> links = queryItemLinks(searchPage);
            if (links.isEmpty()) {
                System.out.println("[Omnisearch] no links via Jsoup, fallback to raw HTML");
                String html = fetchHtml(searchUrl);
                List<SearchResult> candidates = parseSearchResultsFallback(html);
                if (candidates.isEmpty()) {
                    // 尝试主站检索页
                    String altUrl = BASE_URL + "/search.html?key=" + encodedItemName;
                    System.out.println("[Omnisearch] try alt URL: " + altUrl);
                    String altHtml = fetchHtml(altUrl);
                    candidates = parseSearchResultsFallback(altHtml);
                }
                return candidates.isEmpty() ? null : FetchResult.withSearchResults(candidates);
            }
            List<Element> ordered = prioritizeResults(itemName, links);
            List<SearchResult> candidates = ordered.stream()
                    .map(McmodFetcher::toSearchResult)
                    .collect(Collectors.toList());
            return FetchResult.withSearchResults(candidates);
        } else {
            String html = fetchHtml(searchUrl);
            List<SearchResult> candidates = parseSearchResultsFallback(html);
            if (candidates.isEmpty()) return null;
            return FetchResult.withSearchResults(candidates);
        }
    }

    private static Document fetchSearchPage(String searchUrl) throws IOException {
        return Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .timeout(5000)
                .get();
    }

    private static String fetchHtml(String url) throws IOException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5)).build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", BASE_URL)
                .timeout(java.time.Duration.ofSeconds(8))
                .GET().build();
        try {
            java.net.http.HttpResponse<String> resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            System.out.println("[Omnisearch] fetched html length=" + (body == null ? 0 : body.length()));
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
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

    private static List<SearchResult> parseSearchResultsFallback(String html) {
        List<SearchResult> out = new ArrayList<>();
        // 粗略解析 anchor：href 里包含 /item/
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<a[^>]*href=\"(?<href>(?:https?://)?(?:www\\.)?mcmod\\.cn/item/[^\"]+|/item/[^\"]+)\"[^>]*>(?<text>.*?)</a>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(html);
        while (m.find()) {
            String href = m.group("href");
            String text = m.group("text").replaceAll("<[^>]+>", "").trim();
            if (!href.contains("/item/")) continue;
            if (text.matches("(?i)^\n?\s*(?:https?://)?(?:www\\.)?mcmod\\.cn/item/.*")) continue;
            String url = href.startsWith("http") ? href : BASE_URL + href;
            String itemName = extractItemName(text);
            String modName = extractModName(text);
            out.add(new SearchResult(itemName, modName, url));
        }
        System.out.println("[Omnisearch] fallback candidates=" + out.size());
        return out;
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
        if (hasJsoup()) {
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
            return new ItemData(title, modName, itemUrl, htmlContent);
        } else {
            String html = fetchHtml(itemUrl);
            // 标题
            String title = "标题未找到";
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("<div\\s+class=\\\"itemname\\\">[\\s\\S]*?<h5>(.*?)</h5>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
            if (mt.find()) title = mt.group(1).replaceAll("<[^>]+>", "").trim();
            // 模组名
            String modName = "未知";
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("<a[^>]*class=\\\"item\\\"[^>]*href=\\\"/class/[^\\\"]+\\\"[^>]*>(.*?)</a>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
            String lastText = null; while (mm.find()) { lastText = mm.group(1); }
            if (lastText != null) modName = lastText.replaceAll("<[^>]+>", "").trim();
            // 内容容器
            String htmlContent = "<p>暂无详细介绍。</p>";
            java.util.regex.Matcher md = java.util.regex.Pattern.compile("<div[^>]*class=\\\"item-content\\s+common-text\\s+font14\\\"[^>]*>([\\s\\S]*?)</div>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
            if (md.find()) {
                htmlContent = md.group(1);
            }
            return new ItemData(title, modName, itemUrl, htmlContent);
        }
    }
}