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

/**
 * The OmnisearchScreen class provides a user interface for searching items from Mcmod.cn.
 * It features a slide-in panel with a search box, results list, and detailed item view.
 * The screen persists its state across openings and closings within the same game session.
 */
public class OmnisearchScreen extends Screen {
    private EditBox searchBox;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private long animationStartTime;
    private static final long ANIMATION_DURATION = 200; // ms
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

    // Static fields for persistence
    private static ItemData lastSearchResult;
    private static List<SearchResult> lastSearchResults;
    private static boolean wasViewingDetails;
    private static double lastScrollOffset;

    /**
     * Constructs a new OmnisearchScreen.
     */
    public OmnisearchScreen() {
        super(Component.literal("Omnisearch"));
    }

    /**
     * Called when the screen is being closed. Saves the current UI state and initiates the closing animation.
     */
    @Override
    public void onClose() {
        // Save state before closing
        lastSearchResult = this.searchResult;
        lastSearchResults = this.searchResults;
        wasViewingDetails = this.isViewingDetails;
        lastScrollOffset = this.scrollOffset;

        this.isClosing = true;
        this.animationStartTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        try {
            super.init();

            // Store searchbox text if it exists
            String searchBoxText = (this.searchBox != null) ? this.searchBox.getValue() : "";

            this.panelWidth = 200;
            this.panelHeight = (int) (this.height * 0.7);
            this.panelX = this.width - this.panelWidth - 7;
            this.panelY = (this.height - this.panelHeight) / 2;

            int searchBoxWidth = this.panelWidth - 30;
            int searchBoxHeight = 15;
            int searchBoxX = this.panelX + 20;
            int searchBoxY = this.panelY + this.panelHeight - searchBoxHeight - 10;

            // Always re-create widgets d safer prctice.
            this.searchBox = new EditBox(this.font, searchBoxX, searchBoxY, searchBoxWidth, searchBoxHeight, Component.literal("Search..."));
            this.htmlRenderer = new HtmlRenderer(this::updateContentHeight);
            this.backButton = new ClickableEntry("< 返回", () -> {
                this.isViewingDetails = false;
                this.searchResult = null;
                this.scrollOffset = 0; // Reset scroll on back
                OmnisearchScreen.wasViewingDetails = false; // Update static state
                OmnisearchScreen.lastSearchResult = null; // Clear last search result as well
            });
            this.urlEntry = new ClickableEntry("", () -> {
                if (this.searchResult != null && this.searchResult.url() != null) {
                    try {
                        Util.getPlatform().openUri(this.searchResult.url());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            this.searchBox.setValue(searchBoxText);
            addRenderableWidget(this.searchBox);
            setInitialFocus(this.searchBox);

            restoreState();
            this.animationStartTime = System.currentTimeMillis();

            // When resizing, we need to re-calculate content height based on new width
            if (isViewingDetails && searchResult != null) {
                this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url());
                updateContentHeight();
            } else if (!isViewingDetails && clickableResults != null) {
                    // Recalculate height for search results list
                    this.contentHeight = 0;
                    for (ClickableEntry clickable : this.clickableResults) {
                        this.contentHeight += clickable.getHeight() + 5; // 5 for padding
                    }
                }
        } catch (Throwable e) {
            System.err.println("OmnisearchScreen.init() crashed!");
            e.printStackTrace();
            // Close the screen to prevent a full game crash
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
        }
    }

    /**
     * Restores the UI state from static fields, allowing the screen to reopen in the same state it was closed.
     */
    private void restoreState() {
        // Restore state
        if (wasViewingDetails && lastSearchResult != null) {
            this.searchResult = lastSearchResult;
            this.isViewingDetails = true;
            this.scrollOffset = lastScrollOffset;
            this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url());
            updateContentHeight();
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
                    this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url());
                    updateContentHeight();

                    // Update static state for persistence
                    lastSearchResult = this.searchResult;
                    wasViewingDetails = true;
                    lastSearchResults = null;
                }, this.minecraft::execute);
            }));
        }
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if (pKeyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        if (this.searchBox.isFocused() && (pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            String searchText = this.searchBox.getValue();
            if (!searchText.trim().isEmpty()) {
                performSearch(searchText);
            }
            return true;
        }

        return this.searchBox.keyPressed(pKeyCode, pScanCode, pModifiers) || super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    /**
     * Executes an asynchronous search for the given text, updating the UI with the results.
     * @param searchText The text to search for.
     */
    private void performSearch(String searchText) {
        // Clear previous static results on new search
        lastSearchResult = null;
        lastSearchResults = null;
        wasViewingDetails = false;
        lastScrollOffset = 0;

        this.isLoading = true;
        this.noResults = false;
        this.searchResult = null;
        this.searchResults = null;
        this.clickableResults = null;
        this.isViewingDetails = false;
        this.scrollOffset = 0;

        McmodFetcher.fetchItemData(searchText).thenAcceptAsync(result -> {
            this.isLoading = false;
            if (result != null) {
                if (result.isItemData()) {
                    handleItemDataResult(result.getItemData());
                } else if (result.isSearchResults()) {
                    handleSearchResults(result.getSearchResults());
                }
            } else {
                this.noResults = true;
                // Clear static state
                lastSearchResult = null;
                lastSearchResults = null;
                wasViewingDetails = false;
            }
        }, this.minecraft).exceptionally(ex -> {
            this.isLoading = false;
            this.noResults = true; // Show no results on error
            System.err.println("An error occurred during fetch: " + ex.getMessage());
            ex.printStackTrace();
            // Clear static state
            lastSearchResult = null;
            lastSearchResults = null;
            wasViewingDetails = false;
            return null;
        });
    }

    private void handleItemDataResult(ItemData itemData) {
        this.searchResult = itemData;
        this.htmlRenderer.prepare(this.searchResult.htmlContent(), this.panelWidth - 40, this.searchResult.url());
        updateContentHeight(); // Initial content height calculation
        this.isViewingDetails = true;
        this.scrollOffset = 0;
        // Update static state
        lastSearchResult = this.searchResult;
        wasViewingDetails = true;
        lastSearchResults = null;
    }

    private void handleSearchResults(List<SearchResult> searchResults) {
        this.searchResults = searchResults;
        this.isViewingDetails = false;
        // Update static state
        lastSearchResults = this.searchResults;
        wasViewingDetails = false;
        lastSearchResult = null;

        buildClickableResults();
    }



    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        boolean isClickInsidePanel = pMouseX >= this.panelX && pMouseX <= this.panelX + this.panelWidth &&
                                   pMouseY >= this.panelY && pMouseY <= this.panelY + this.panelHeight;

        if (!isClickInsidePanel) {
            this.onClose();
            return true;
        }

        if (handleDetailViewClick(pMouseX, pMouseY)) {
            return true;
        }

        // Handle link clicks
        if (this.isViewingDetails && this.htmlRenderer != null) {
            String url = this.htmlRenderer.getLinkUrlAt((int) pMouseX, (int) pMouseY, this.panelX + 20, this.panelY + 45);
            if (url != null) {
                try {
                    Util.getPlatform().openUri(url);
                    return true;
                } catch (Exception e) {
                    System.err.println("Could not open link: " + e.getMessage());
                }
            }
        }

        if (handleResultListClick(pMouseX, pMouseY)) {
            return true;
        }

        if (handleScrollbarClick(pMouseX, pMouseY, pButton)) {
            return true;
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    /**
     * Handles clicks within the detail view, specifically the back button and URL link.
     * @param pMouseX The x-coordinate of the mouse.
     * @param pMouseY The y-coordinate of the mouse.
     * @return {@code true} if a click was handled, {@code false} otherwise.
     */
    private boolean handleDetailViewClick(double pMouseX, double pMouseY) {
        if (this.isViewingDetails) {
            if (this.backButton.mouseClicked(pMouseX, pMouseY)) {
                return true;
            }
            if (this.urlEntry.mouseClicked(pMouseX, pMouseY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles clicks on the list of search results.
     * @param pMouseX The x-coordinate of the mouse.
     * @param pMouseY The y-coordinate of the mouse.
     * @return {@code true} if a click was handled, {@code false} otherwise.
     */
    private boolean handleResultListClick(double pMouseX, double pMouseY) {
        if (this.clickableResults != null && !this.isViewingDetails) {
            for (ClickableEntry clickable : this.clickableResults) {
                if (clickable.mouseClicked(pMouseX, pMouseY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handles clicks on the scrollbar to initiate dragging.
     * @param pMouseX The x-coordinate of the mouse.
     * @param pMouseY The y-coordinate of the mouse.
     * @param pButton The mouse button that was clicked.
     * @return {@code true} if the scrollbar was clicked, {@code false} otherwise.
     */
    private boolean handleScrollbarClick(double pMouseX, double pMouseY, int pButton) {
        if (this.contentHeight > this.panelHeight - 60) {
            int scrollbarX = this.panelX + this.panelWidth - 6;
            int scrollbarY = this.panelY + 28;
            int scrollbarWidth = 3;
            int scrollbarHeight = this.panelHeight - 60;
            if (pButton == 0 && pMouseX >= scrollbarX && pMouseX < scrollbarX + scrollbarWidth && pMouseY >= scrollbarY && pMouseY < scrollbarY + scrollbarHeight) {
                this.isDragging = true;
                this.dragStartMouseY = pMouseY;
                this.dragStartScrollOffset = this.scrollOffset;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0) {
            this.isDragging = false;
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (this.isDragging) {
            handleMouseDrag(pMouseY);
            return true;
        }
        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    /**
     * Manages the scrolling logic when the scrollbar is being dragged.
     * @param pMouseY The current y-coordinate of the mouse.
     */
    private void handleMouseDrag(double pMouseY) {
        double scrollableHeight = this.panelHeight - 60;
        int maxScroll = Math.max(0, this.contentHeight - (this.panelHeight - 60));
        double scrollDelta = (pMouseY - this.dragStartMouseY) / scrollableHeight * maxScroll;
        this.scrollOffset = Math.max(0, Math.min(this.dragStartScrollOffset + scrollDelta, maxScroll));
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        int maxScroll = Math.max(0, this.contentHeight - (this.panelHeight - 60)); // 60 for margins
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset - pScrollY * 20, maxScroll));
        return true;
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        long elapsedTime = System.currentTimeMillis() - this.animationStartTime;
        float progress = Math.min((float)elapsedTime / ANIMATION_DURATION, 1.0f);

        if (this.isClosing) {
            progress = 1.0f - progress;
        }

        float easedProgress = 1 - (float)Math.pow(1 - progress, 3); // Ease-out cubic

        if (this.isClosing && progress <= 0.0f) {
            this.minecraft.setScreen(null);
            return;
        }

        int initialPanelX = this.width;
        int finalPanelX = this.width - this.panelWidth - 10;
        this.panelX = (int)(initialPanelX + (finalPanelX - initialPanelX) * easedProgress);

        renderUI(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
    }

    /**
     * Renders the main user interface of the search panel.
     * @param pGuiGraphics The GuiGraphics object for rendering.
     * @param pMouseX The x-coordinate of the mouse.
     * @param pMouseY The y-coordinate of the mouse.
     * @param pPartialTick The partial tick time.
     */
    private void renderUI(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        ModernUI.renderPanel(pGuiGraphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.title.getString(), this.font);

        if (this.isViewingDetails) {
            this.backButton.setBounds(this.panelX + 15, this.panelY + 10, this.font.width(this.backButton.getText()), this.font.lineHeight);
            ModernUI.renderClickable(pGuiGraphics, this.font, this.backButton, pMouseX, pMouseY);
        }

        if (this.isLoading) {
            ModernUI.renderLoading(pGuiGraphics, this.font, this.panelX, this.panelWidth, this.panelY, this.panelHeight);
        } else if (this.noResults) {
            ModernUI.renderNoResults(pGuiGraphics, this.font, this.panelX, this.panelWidth, this.panelY, this.panelHeight);
        } else if (this.searchResults != null && this.clickableResults != null && !this.isViewingDetails) {
            this.contentHeight = 0;
            for (ClickableEntry clickable : this.clickableResults) {
                this.contentHeight += clickable.getHeight() + 5; // 5 for padding
            }
            ModernUI.renderSearchResults(pGuiGraphics, this.font, this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.clickableResults, this.scrollOffset, pMouseX, pMouseY);
        } else if (this.searchResult != null && this.isViewingDetails) {
            ModernUI.renderItemDetails(pGuiGraphics, this.font, this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.searchResult, this.htmlRenderer, this.scrollOffset, pMouseX, pMouseY, this.urlEntry);
        }

        // Update search box position
        this.searchBox.setX(this.panelX + 15);
        this.searchBox.setY(this.panelY + this.panelHeight - this.searchBox.getHeight() - 10);

        // Render the search box background
        ModernUI.renderSearchBox(pGuiGraphics, this.searchBox.getX() - 2, this.searchBox.getY() - 2, this.searchBox.getWidth() + 4, this.searchBox.getHeight() + 4);

        // Render the search box
        this.searchBox.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        // Render scrollbar
        renderScrollbar(pGuiGraphics, pMouseX, pMouseY);
    }

    private void updateContentHeight() {
        if (this.isViewingDetails && this.searchResult != null && this.htmlRenderer != null) {
            int metadataHeight = ModernUI.getMetadataBlockHeight(this.font, this.panelWidth - 30, this.searchResult);
            // Total height = 5px (top margin) + metadataHeight + 15px (spacing) + htmlContentHeight
            this.contentHeight = metadataHeight + this.htmlRenderer.getContentHeight() + 20;
        } else if (!this.isViewingDetails && this.clickableResults != null) {
            this.contentHeight = 15; // "Found multiple results:" text
            for (ClickableEntry result : this.clickableResults) {
                this.contentHeight += result.getHeight();
            }
        } else {
            this.contentHeight = 0;
        }
    }

    /**
     * Renders the scrollbar if the content is taller than the visible area.
     * @param pGuiGraphics The GuiGraphics object for rendering.
     * @param pMouseX The x-coordinate of the mouse.
     * @param pMouseY The y-coordinate of the mouse.
     */
    private void renderScrollbar(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        if (this.contentHeight > this.panelHeight - 60) {
            int scrollbarX = this.panelX + this.panelWidth - 6;
            int scrollbarY = this.panelY + 28;
            int scrollbarWidth = 3;
            int scrollbarHeight = this.panelHeight - 60;
            int maxScroll = Math.max(0, this.contentHeight - scrollbarHeight);

            if (this.isDragging) {
                pGuiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0x80000000);
            }

            int thumbHeight = Math.max(30, (int) ((double)scrollbarHeight / this.contentHeight * scrollbarHeight));
            int thumbY = scrollbarY;
            if (maxScroll > 0) {
                thumbY += (int) (this.scrollOffset / maxScroll * (scrollbarHeight - thumbHeight));
            }

            boolean isMouseOverScrollbar = pMouseX >= scrollbarX && pMouseX <= scrollbarX + scrollbarWidth && pMouseY >= scrollbarY && pMouseY <= scrollbarY + scrollbarHeight;
            int thumbColor = (this.isDragging || isMouseOverScrollbar) ? 0xFFCCCCCC : 0xFF888888;

            pGuiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}