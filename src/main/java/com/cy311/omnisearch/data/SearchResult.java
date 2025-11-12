package com.cy311.omnisearch.data;

public class SearchResult {
    private final String itemName;
    private final String modName;
    private final String url;

    public SearchResult(String itemName, String modName, String url) {
        this.itemName = itemName;
        this.modName = modName;
        this.url = url;
    }

    public String getItemName() {
        return itemName;
    }

    public String getModName() {
        return modName;
    }

    public String getUrl() {
        return url;
    }

    public String getDisplayText() {
        if (modName != null && !modName.isEmpty() && !modName.equals("vanilla_or_unknown")) {
            return itemName + " (" + modName + ")";
        }
        return itemName;
    }
}
