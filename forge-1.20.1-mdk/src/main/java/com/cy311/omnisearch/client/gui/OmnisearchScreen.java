package com.cy311.omnisearch.client.gui;

import com.cy311.omnisearch.data.ItemData;
import com.cy311.omnisearch.data.SearchResult;
import com.cy311.omnisearch.util.McmodFetcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;

public class OmnisearchScreen extends Screen {
    private EditBox searchBox;
    private net.minecraft.client.gui.components.Button searchButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private long animationStartTime;
    private static final long ANIMATION_DURATION = 200;
    private boolean isClosing = false;
    private ItemData searchResult;
    private List<SearchResult> searchResults;
    private List<ClickableEntry> clickableResults;
    private ClickableEntry backButton;
    private ClickableEntry urlEntry;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private boolean isLoading = false;
    private boolean noResults = false;
    private boolean isViewingDetails = false;
    private HtmlRenderer htmlRenderer;
    private boolean isDragging = false;
    private double dragStartMouseY = -1;
    private double dragStartScrollOffset = -1;
    private String initialSearchTerm = null;
    private final Screen parentScreen;
    private boolean pendingSubmit = false;
    private static ItemData lastSearchResult;
    private static List<SearchResult> lastSearchResults;
    private static boolean wasViewingDetails;
    private static double lastScrollOffset;

    public OmnisearchScreen() { this(null, null); }
    public OmnisearchScreen(String initialSearchTerm) { this(null, initialSearchTerm); }
    public OmnisearchScreen(Screen parent, String initialSearchTerm) {
        super(Component.literal("Omnisearch"));
        this.parentScreen = parent;
        this.initialSearchTerm = initialSearchTerm;
    }

    public void onClose() {
        lastSearchResult = this.searchResult;
        lastSearchResults = this.searchResults;
        wasViewingDetails = this.isViewingDetails;
        lastScrollOffset = this.scrollOffset;
        if (this.parentScreen != null) {
            this.minecraft.setScreen(this.parentScreen);
        } else {
            this.isClosing = true;
            this.animationStartTime = System.currentTimeMillis();
        }
    }

    protected void init() {
        try {
            super.init();
            String searchBoxText = (this.searchBox != null) ? this.searchBox.getValue() : "";
            this.panelWidth = 200;
            this.panelHeight = (int) (this.height * 0.7);
            this.panelX = this.width - this.panelWidth - 7;
            this.panelY = (this.height - this.panelHeight) / 2;
            int searchBoxWidth = this.panelWidth - 30;
            int searchBoxHeight = 15;
            int searchBoxX = this.panelX + 20;
            int searchBoxY = this.panelY + this.panelHeight - searchBoxHeight - 10;
            int buttonWidth = 60;
            int spacing = 5;
            int editWidth = searchBoxWidth - buttonWidth - spacing;
            this.searchBox = new EditBox(this.font, searchBoxX, searchBoxY, editWidth, searchBoxHeight, Component.literal("Search..."));
            this.searchButton = net.minecraft.client.gui.components.Button.builder(Component.literal("搜索"), b -> submitSearch())
                    .bounds(searchBoxX + editWidth + spacing, searchBoxY, buttonWidth, searchBoxHeight)
                    .build();
            this.htmlRenderer = null;
            this.backButton = new ClickableEntry("< 返回", () -> {
                this.isViewingDetails = false;
                this.searchResult = null;
                this.scrollOffset = 0;
                OmnisearchScreen.wasViewingDetails = false;
                OmnisearchScreen.lastSearchResult = null;
            });
            this.urlEntry = new ClickableEntry("", () -> {
                if (this.searchResult != null && this.searchResult.url() != null) {
                    try { Util.getPlatform().openUri(this.searchResult.url()); } catch (Exception e) { e.printStackTrace(); }
                }
            });
            this.searchBox.setValue(searchBoxText);
            addRenderableWidget(this.searchBox);
            addRenderableWidget(this.searchButton);
            setInitialFocus(this.searchBox);
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
            restoreState();
            this.animationStartTime = System.currentTimeMillis();
            if (this.initialSearchTerm != null && !this.initialSearchTerm.isEmpty()) {
                this.searchBox.setValue(this.initialSearchTerm);
                performSearch(this.initialSearchTerm);
                this.initialSearchTerm = null;
            }
            if (isViewingDetails && searchResult != null) {
                ensureHtmlRenderer();
                if (this.htmlRenderer != null) {
                    this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url());
                    updateContentHeight();
                }
            } else if (!isViewingDetails && clickableResults != null) {
                this.contentHeight = 0;
                for (ClickableEntry clickable : this.clickableResults) {
                    this.contentHeight += clickable.getHeight() + 5;
                }
            }
        } catch (Throwable e) {
            System.err.println("OmnisearchScreen.init() crashed!");
            e.printStackTrace();
            if (this.minecraft != null) { this.minecraft.setScreen(null); }
        }
    }

    private void restoreState() {
        if (wasViewingDetails && lastSearchResult != null) {
            this.searchResult = lastSearchResult;
            this.isViewingDetails = true;
            this.scrollOffset = lastScrollOffset;
            ensureHtmlRenderer();
            if (this.htmlRenderer != null) {
                this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url());
                updateContentHeight();
            }
        } else if (!wasViewingDetails && lastSearchResults != null) {
            this.searchResults = lastSearchResults;
            this.isViewingDetails = false;
            this.scrollOffset = lastScrollOffset;
            buildClickableResults();
        }
    }

    private void buildClickableResults() {
        this.clickableResults = new ArrayList<>();
        if (this.searchResults == null) return;
        for (SearchResult res : this.searchResults) {
            this.clickableResults.add(new ClickableEntry(res.getItemName(), res.getModName(), () -> {
                this.isLoading = true;
                McmodFetcher.fetchItemDetails(res.getUrl()).thenAcceptAsync(itemData -> {
                    this.isLoading = false;
                    this.searchResult = itemData;
                    this.isViewingDetails = true;
                    this.scrollOffset = 0;
                    ensureHtmlRenderer();
                    if (this.htmlRenderer != null) {
                        this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url());
                        updateContentHeight();
                    }
                    lastSearchResult = this.searchResult;
                    wasViewingDetails = true;
                    lastSearchResults = null;
                }, this.minecraft::execute);
            }));
        }
    }

    private void ensureHtmlRenderer() {
        if (this.htmlRenderer != null) return;
        try { this.htmlRenderer = new HtmlRenderer(this::updateContentHeight); }
        catch (Throwable t) { System.err.println("HtmlRenderer unavailable: " + t); this.htmlRenderer = null; this.isViewingDetails = false; this.noResults = false; }
    }

    public void submitSearch() {
        if (this.searchBox == null) return;
        String searchText = this.searchBox.getValue();
        if (searchText != null && !searchText.trim().isEmpty()) {
            performSearch(searchText.trim());
        }
    }
    public void requestSubmit() { this.pendingSubmit = true; }

    private void performSearch(String searchText) {
        lastSearchResult = null; lastSearchResults = null; wasViewingDetails = false; lastScrollOffset = 0;
        this.isLoading = true; this.noResults = false; this.searchResult = null; this.searchResults = null; this.clickableResults = null; this.isViewingDetails = false; this.scrollOffset = 0;
        com.cy311.omnisearch.util.McmodFetcher.fetchItemData(searchText).thenAcceptAsync(result -> {
            this.isLoading = false;
            if (result != null) {
                if (result.isItemData()) { handleItemDataResult(result.getItemData()); }
                else if (result.isSearchResults()) { handleSearchResults(result.getSearchResults()); }
            } else {
                this.noResults = true; lastSearchResult = null; lastSearchResults = null; wasViewingDetails = false;
            }
        }, this.minecraft).exceptionally(ex -> { this.isLoading = false; this.noResults = true; ex.printStackTrace(); lastSearchResult = null; lastSearchResults = null; wasViewingDetails = false; return null; });
    }

    private void handleItemDataResult(ItemData itemData) {
        this.searchResult = itemData; ensureHtmlRenderer();
        if (this.htmlRenderer != null) {
            this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url()); updateContentHeight(); this.isViewingDetails = true;
        } else { this.isViewingDetails = true; }
        this.scrollOffset = 0; lastSearchResult = this.searchResult; wasViewingDetails = true; lastSearchResults = null;
    }

    private void handleSearchResults(List<SearchResult> searchResults) {
        this.searchResults = searchResults; this.isViewingDetails = false; lastSearchResults = this.searchResults; wasViewingDetails = false; lastSearchResult = null; buildClickableResults();
    }

    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        boolean isClickInsidePanel = pMouseX >= this.panelX && pMouseX <= this.panelX + this.panelWidth && pMouseY >= this.panelY && pMouseY <= this.panelY + this.panelHeight;
        if (!isClickInsidePanel) { this.onClose(); return true; }
        if (handleDetailViewClick(pMouseX, pMouseY)) { return true; }
        if (this.isViewingDetails && this.htmlRenderer != null) {
            String url = this.htmlRenderer.getLinkUrlAt((int) pMouseX, (int) pMouseY, this.panelX + 20, this.panelY + 45);
            if (url != null) { try { Util.getPlatform().openUri(url); return true; } catch (Exception e) { System.err.println("Could not open link: " + e.getMessage()); } }
        }
        if (handleResultListClick(pMouseX, pMouseY)) { return true; }
        if (handleScrollbarClick(pMouseX, pMouseY, pButton)) { return true; }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    private boolean handleDetailViewClick(double pMouseX, double pMouseY) {
        if (this.isViewingDetails) { if (this.backButton.mouseClicked(pMouseX, pMouseY)) { return true; } if (this.urlEntry.mouseClicked(pMouseX, pMouseY)) { return true; } }
        return false;
    }

    private boolean handleResultListClick(double pMouseX, double pMouseY) {
        if (this.clickableResults != null && !this.isViewingDetails) { for (ClickableEntry clickable : this.clickableResults) { if (clickable.mouseClicked(pMouseX, pMouseY)) { return true; } } }
        return false;
    }

    private boolean handleScrollbarClick(double pMouseX, double pMouseY, int pButton) {
        if (this.contentHeight > this.panelHeight - 60) {
            int scrollbarX = this.panelX + this.panelWidth - 6;
            int scrollbarY = this.panelY + 28;
            int scrollbarWidth = 3;
            int scrollbarHeight = this.panelHeight - 60;
            if (pButton == 0 && pMouseX >= scrollbarX && pMouseX < scrollbarX + scrollbarWidth && pMouseY >= scrollbarY && pMouseY < scrollbarY + scrollbarHeight) {
                this.isDragging = true; this.dragStartMouseY = pMouseY; this.dragStartScrollOffset = this.scrollOffset; return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) { if (pButton == 0) this.isDragging = false; return false; }
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) { if (this.isDragging) { handleMouseDrag(pMouseY); return true; } return false; }
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) { if (pKeyCode == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; } return super.keyPressed(pKeyCode, pScanCode, pModifiers); }
    private void handleMouseDrag(double pMouseY) { double scrollableHeight = this.panelHeight - 60; int maxScroll = Math.max(0, this.contentHeight - (this.panelHeight - 60)); double scrollDelta = (pMouseY - this.dragStartMouseY) / scrollableHeight * maxScroll; this.scrollOffset = Math.max(0, Math.min(this.dragStartScrollOffset + scrollDelta, maxScroll)); }
    public boolean onWheel(double delta) {
        int maxScroll = Math.max(0, this.contentHeight - (this.panelHeight - 60));
        if (maxScroll <= 0) return false;
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset - delta * 20, maxScroll));
        return true;
    }
    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        com.cy311.omnisearch.OmnisearchLogger.info("OmnisearchScreen.mouseScrolled delta={}", pDelta);
        return onWheel(pDelta) || super.mouseScrolled(pMouseX, pMouseY, pDelta);
    }

    @Override
    public void tick() { super.tick(); if (this.searchBox != null) { this.searchBox.tick(); } }

    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        long currentTime = System.currentTimeMillis(); long elapsedTime = currentTime - animationStartTime; float progress = Math.min((float)elapsedTime / ANIMATION_DURATION, 1.0f); if (this.isClosing) { progress = 1.0f - progress; }
        float easedProgress = 1 - (float)Math.pow(1 - progress, 3);
        if (this.isClosing && progress <= 0.0f) { this.minecraft.setScreen(null); return; }
        int initialPanelX = this.width; int finalPanelX = this.width - this.panelWidth - 10; this.panelX = (int)(initialPanelX + (finalPanelX - initialPanelX) * easedProgress);
        renderUI(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        if (this.pendingSubmit) { this.pendingSubmit = false; submitSearch(); }
    }

    private void renderUI(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        ModernUI.UIContext ctx = new ModernUI.UIContext(pGuiGraphics, this.font, this.panelX, this.panelY, this.panelWidth, this.panelHeight, pMouseX, pMouseY, this.scrollOffset);
        ModernUI.renderPanel(ctx, this.title.getString());
        if (this.isViewingDetails) { this.backButton.setBounds(this.panelX + 15, this.panelY + 10, this.font.width(this.backButton.getText()), this.font.lineHeight); ModernUI.renderClickable(ctx, this.backButton); }
        if (this.isLoading) { ModernUI.renderLoading(ctx); }
        else if (this.noResults) { ModernUI.renderNoResults(ctx); }
        else if (this.searchResults != null && this.clickableResults != null && !this.isViewingDetails) { this.contentHeight = 0; for (ClickableEntry clickable : this.clickableResults) { this.contentHeight += clickable.getHeight() + 5; } ModernUI.renderSearchResults(ctx, this.clickableResults); }
        else if (this.searchResult != null && this.isViewingDetails) { ModernUI.renderItemDetails(ctx, this.searchResult, this.htmlRenderer, this.urlEntry); }
        this.searchBox.setX(this.panelX + 15); this.searchBox.setY(this.panelY + this.panelHeight - this.searchBox.getHeight() - 10); this.searchButton.setX(this.searchBox.getX() + this.searchBox.getWidth() + 5); this.searchButton.setY(this.searchBox.getY());
        ModernUI.renderSearchBox(ctx, this.searchBox.getX() - 2, this.searchBox.getY() - 2, this.searchBox.getWidth() + 4 + 65, this.searchBox.getHeight() + 4);
        if (this.searchBox.isFocused()) { int gx0 = this.searchBox.getX() - 2; int gy0 = this.searchBox.getY() - 2; int gx1 = this.searchBox.getX() + this.searchBox.getWidth() + 2; int gy1 = this.searchBox.getY() + this.searchBox.getHeight() + 2; ctx.g.fill(gx0, gy0, gx1, gy1, 0x332C98F0); }
        this.searchBox.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        renderScrollbar(pGuiGraphics, pMouseX, pMouseY);
    }

    private void updateContentHeight() {
        if (this.isViewingDetails && this.searchResult != null && this.htmlRenderer != null) {
            int metadataHeight = ModernUI.getMetadataBlockHeight(this.font, this.panelWidth - 30, this.searchResult);
            this.contentHeight = metadataHeight + this.htmlRenderer.getContentHeight() + 20;
        } else if (!this.isViewingDetails && this.clickableResults != null) {
            this.contentHeight = 15; for (ClickableEntry result : this.clickableResults) { this.contentHeight += result.getHeight(); }
        } else { this.contentHeight = 0; }
    }

    private void renderScrollbar(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        if (this.contentHeight > this.panelHeight - 60) {
            int scrollbarX = this.panelX + this.panelWidth - 6; int scrollbarY = this.panelY + 28; int scrollbarWidth = 3; int scrollbarHeight = this.panelHeight - 60; int maxScroll = Math.max(0, this.contentHeight - scrollbarHeight);
            pGuiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0x26202225);
            int thumbHeight = Math.max(30, (int) ((double)scrollbarHeight / this.contentHeight * scrollbarHeight)); int thumbY = scrollbarY; if (maxScroll > 0) { thumbY += (int) (this.scrollOffset / maxScroll * (scrollbarHeight - thumbHeight)); }
            boolean isMouseOverScrollbar = pMouseX >= scrollbarX && pMouseX <= scrollbarX + scrollbarWidth && pMouseY >= scrollbarY && pMouseY <= scrollbarY + scrollbarHeight; int thumbColor = (this.isDragging || isMouseOverScrollbar) ? 0xFFB6C2FF : 0xFF9499A3; pGuiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor); pGuiGraphics.hLine(scrollbarX, scrollbarX + scrollbarWidth - 1, thumbY, 0x40202225); pGuiGraphics.hLine(scrollbarX, scrollbarX + scrollbarWidth - 1, thumbY + thumbHeight - 1, 0x40202225);
        }
    }

    public boolean isPauseScreen() { return true; }
}