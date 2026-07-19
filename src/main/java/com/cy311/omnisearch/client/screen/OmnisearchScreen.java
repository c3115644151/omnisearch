package com.cy311.omnisearch.client.screen;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.client.render.*;
import com.cy311.omnisearch.client.render.image.ImageManager;
import com.cy311.omnisearch.client.screen.state.OmnisearchWindowReducer;
import com.cy311.omnisearch.client.screen.state.OmnisearchWindowState;
import com.cy311.omnisearch.client.screen.state.SearchSessionState;
import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.PendingRequest;
import com.cy311.omnisearch.data.model.PendingRequestResult;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.repository.SearchRepository;
import com.cy311.omnisearch.data.source.CaptchaRequiredException;
import com.cy311.omnisearch.gui.animation.SlideAnimation;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import com.cy311.omnisearch.search.SearchEvent;
import com.cy311.omnisearch.search.SearchState;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jetbrains.annotations.Nullable;


// verified: Screen, Minecraft.setScreen(), GuiGraphics — standard MC API, stable
// verified: EditBox from NeoForge 1.21.1 lexxie.dev 2026-06-14
// verified: Util.NULL from NeoForge 1.21.1 2026-06-14
public class OmnisearchScreen extends Screen {
    private OmnisearchWindowState uiState;
    private final SearchRepository repo;
    private SearchBarWidget sb;
    private SearchResultsPane resultsPane;
    private DetailContentPane detailPane;
    private CaptchaDialogWidget cd;
    private EditBox captchaInput;
    private CaptchaImageRenderer captchaImage;
    private ImageManager imageManager;
    private final java.util.function.Function<String, byte[]> imageDownloader;
    private long searchSeq;
    private long detailSeq;
    private CompletableFuture<?> searchOp;
    private CompletableFuture<?> detailOp;
    private final SlideAnimation slide = new SlideAnimation();
    private final FloatingSearchWindow floatingWindow = new FloatingSearchWindow();
    // Clear cache flash message
    private int clearCacheFlashTicks = 0;
    private static final int SBW = 300;

    // Optional initial query for hover-to-search
    @Nullable
    private final String initialQuery;

    public OmnisearchScreen(SearchRepository repo, java.util.function.Function<String, byte[]> imageDownloader) {
        this(repo, imageDownloader, null);
    }

    public OmnisearchScreen(SearchRepository repo, java.util.function.Function<String, byte[]> imageDownloader, @Nullable String initialQuery) {
        super(Component.literal("Omnisearch"));
        OmnisearchMod.LOGGER.debug("Screen constructor called");
        this.repo = repo;
        this.imageDownloader = imageDownloader;
        this.initialQuery = initialQuery;
        this.uiState = OmnisearchWindowState.initial();
    }

    @Override
    protected void init() {
        super.init();
        OmnisearchMod.LOGGER.debug("Screen init() called, width={} height={}", width, height);
        imageManager = new ImageManager(imageDownloader != null ? imageDownloader : url -> null, new com.cy311.omnisearch.data.client.RequestExecutor());
        int cx = (width - SBW) / 2;
        sb = new SearchBarWidget(font, cx, height / 3, SBW);
        resultsPane = new SearchResultsPane(new ResultListWidget(font), font);
        detailPane = new DetailContentPane(new DetailPanelWidget(font), new com.cy311.omnisearch.client.render.document.DocumentRenderer(font, imageManager));
        cd = new CaptchaDialogWidget(font);
        // Captcha input EditBox (hidden until captcha is required)
        captchaInput = new EditBox(font, width / 2 - 120 + 9, 0, 222, 18, Component.literal(""));
        sb.getEditBox().setFocused(true);

        // Auto-submit if opened via hover-to-search with an item name
        if (initialQuery != null && !initialQuery.isBlank()) {
            sb.getEditBox().setValue(initialQuery);
            uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.QueryChanged(initialQuery));
            submitSearch();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        FloatingSearchWindow.Bounds panelBounds = floatingWindow.computeBounds(width, height);
        int searchBarHeight = sb.getTotalHeight();
        switch (uiState.search().currentView()) {
            case SEARCH -> renderSearchWindow(g, panelBounds);
            case RESULTS -> {
                g.pose().pushPose();
                slide.applyTransform(g, width);
                floatingWindow.renderShell(g, width, height, panelBounds);
                int[] searchBounds = floatingWindow.getSearchBarBounds(panelBounds, searchBarHeight);
                int[] bodyBounds = floatingWindow.getBodyBounds(panelBounds, searchBarHeight);
                sb.render(g, searchBounds[0], searchBounds[1], searchBounds[2], uiState.search().query().text(), uiState.search().modFilter(), mx, my);
                uiState = uiState.withSearch(
                    resultsPane.render(g, uiState.search(), bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3], mx, my)
                );
                renderStatusBar(g, panelBounds, currentStatusMessage(), currentStatusColor());
                g.pose().popPose();
            }
            case DETAIL -> {
                g.pose().pushPose();
                slide.applyTransform(g, width);
                renderDetailWindow(g, panelBounds, mx, my);
                renderStatusBar(g, panelBounds, currentStatusMessage(), currentStatusColor());
                g.pose().popPose();
            }
        }
        if (uiState.window().loading() == SearchState.LoadingState.CAPTCHA_REQUIRED) {
            renderCaptcha(g, uiState.window().captcha(), panelBounds, mx, my, d);
        }
        if (clearCacheFlashTicks > 0 && uiState.window().loading() != SearchState.LoadingState.CAPTCHA_REQUIRED) {
            clearCacheFlashTicks--;
        }
    }

    private void renderSearchWindow(GuiGraphics g, FloatingSearchWindow.Bounds panelBounds) {
        int searchBarHeight = sb.getTotalHeight();
        floatingWindow.renderShell(g, width, height, panelBounds);
        int[] searchBounds = floatingWindow.getSearchBarBounds(panelBounds, searchBarHeight);
        int[] bodyBounds = floatingWindow.getBodyBounds(panelBounds, searchBarHeight);
        sb.render(g, searchBounds[0], searchBounds[1], searchBounds[2], uiState.search().query().text());
        g.fill(bodyBounds[0], bodyBounds[1], bodyBounds[0] + bodyBounds[2], bodyBounds[1] + bodyBounds[3], OmniTheme.BG_CONTENT);
        g.drawCenteredString(font, "输入关键词后按回车搜索", bodyBounds[0] + bodyBounds[2] / 2, bodyBounds[1] + Math.max(8, bodyBounds[3] / 2 - font.lineHeight), OmniTheme.TEXT_WHITE);
        g.drawCenteredString(font, "结果将在右侧面板中显示", bodyBounds[0] + bodyBounds[2] / 2, bodyBounds[1] + Math.max(8, bodyBounds[3] / 2 + 4), OmniTheme.TEXT_GRAY);
        renderStatusBar(g, panelBounds, currentStatusMessage(), currentStatusColor());
    }

    private void renderDetailWindow(GuiGraphics g, FloatingSearchWindow.Bounds panelBounds, int mx, int my) {
        int searchBarHeight = sb.getTotalHeight();
        floatingWindow.renderShell(g, width, height, panelBounds);
        int[] searchBounds = floatingWindow.getSearchBarBounds(panelBounds, searchBarHeight);
        int[] bodyBounds = floatingWindow.getBodyBounds(panelBounds, searchBarHeight);
        sb.render(g, searchBounds[0], searchBounds[1], searchBounds[2], uiState.search().query().text());

        if (uiState.detail().page() == null) {
            g.fill(bodyBounds[0], bodyBounds[1], bodyBounds[0] + bodyBounds[2], bodyBounds[1] + bodyBounds[3], OmniTheme.BG_CONTENT);
            g.drawCenteredString(font, "正在加载详情...", bodyBounds[0] + bodyBounds[2] / 2, bodyBounds[1] + Math.max(8, bodyBounds[3] / 2 - font.lineHeight / 2), OmniTheme.TEXT_WHITE);
            return;
        }

        uiState = uiState.withDetail(
            detailPane.render(g, uiState.detail(), bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3], mx, my)
        );
    }

    private void renderStatusBar(GuiGraphics g, FloatingSearchWindow.Bounds panelBounds, String message, int color) {
        int[] statusBounds = floatingWindow.getStatusBounds(panelBounds);
        g.fill(statusBounds[0], statusBounds[1], statusBounds[0] + statusBounds[2], statusBounds[1] + statusBounds[3], OmniTheme.BG_PANEL);
        g.hLine(statusBounds[0], statusBounds[0] + statusBounds[2] - 1, statusBounds[1], OmniTheme.BORDER);
        if (message == null || message.isBlank()) {
            return;
        }
        String text = ellipsize(message, Math.max(10, statusBounds[2] - OmniTheme.PADDING * 2));
        int textY = statusBounds[1] + Math.max(0, (statusBounds[3] - font.lineHeight) / 2);
        g.drawString(font, text, statusBounds[0] + OmniTheme.PADDING, textY, color, false);
    }

    private String currentStatusMessage() {
        if (clearCacheFlashTicks > 0) {
            return "缓存已清除 (F6)";
        }
        return switch (uiState.window().loading()) {
            case LOADING -> uiState.search().currentView() == SearchSessionState.BodyView.DETAIL ? "正在加载详情..." : "Searching...";
            case ERROR -> uiState.window().errorMessage() != null ? uiState.window().errorMessage() : "请求失败";
            case CAPTCHA_REQUIRED -> "需要验证码，完成后继续请求";
            case IDLE -> switch (uiState.search().currentView()) {
                case SEARCH -> "回车搜索  ESC关闭";
                case RESULTS -> "点击结果查看详情  F6清缓存";
                case DETAIL -> "滚轮滚动正文  ESC关闭";
            };
        };
    }

    private int currentStatusColor() {
        if (clearCacheFlashTicks > 0) {
            return 0xFF55FF55;
        }
        return uiState.window().loading() == SearchState.LoadingState.ERROR ? OmniTheme.TEXT_ERROR : OmniTheme.TEXT_GRAY;
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int usableWidth = Math.max(10, maxWidth - font.width("..."));
        return font.plainSubstrByWidth(text, usableWidth) + "...";
    }

    private void renderCaptcha(GuiGraphics g, CaptchaContext captcha, FloatingSearchWindow.Bounds panelBounds, int mx, int my, float d) {
        if (captcha == null) return;
        int dx = panelBounds.x() + (panelBounds.width() - cd.getDialogWidth()) / 2;
        int dy = panelBounds.y() + Math.max(12, (panelBounds.height() - cd.computeDialogHeight()) / 2);
        cd.render(g, dx, dy, captcha);

        int[] imgBounds = cd.getImageBounds(dx, dy);
        int cx = imgBounds[0];
        int cw = imgBounds[2];
        int cy = imgBounds[1];
        int ch = imgBounds[3];

        // Render the real captcha image scaled to fit
        if (captchaImage != null) {
            captchaImage.render(g, cx, cy, cw, ch);
        }

        // Question text
        g.drawCenteredString(font, captcha.captchaId(), dx + 120, cy + ch + 2, OmniTheme.TEXT_HEADING_1);

        // Input field background & border
        int inputY = cy + ch + 14;
        int inputW = cw;
        g.fill(cx, inputY, cx + inputW, inputY + 18, 0xFF000000);
        g.hLine(cx, cx + inputW - 1, inputY, 0xFF373737);
        g.vLine(cx, inputY, inputY + 17, 0xFF373737);
        g.hLine(cx, cx + inputW - 1, inputY + 17, OmniTheme.TEXT_WHITE);
        g.vLine(cx + inputW - 1, inputY, inputY + 17, OmniTheme.TEXT_WHITE);

        // EditBox for captcha answer
        captchaInput.setX(cx + 1);
        captchaInput.setY(inputY + 1);
        captchaInput.setWidth(inputW - 2);
        captchaInput.render(g, mx, my, d);

        // Submit button
        int btnX = dx + 120 - 30;
        int btnY = inputY + 18 + 4;
        g.hLine(btnX, btnX + 59, btnY, OmniTheme.TEXT_WHITE);
        g.vLine(btnX, btnY, btnY + 19, OmniTheme.TEXT_WHITE);
        g.hLine(btnX, btnX + 59, btnY + 19, 0xFF373737);
        g.vLine(btnX + 59, btnY, btnY + 19, 0xFF373737);
        g.fill(btnX + 1, btnY + 1, btnX + 59, btnY + 19, 0xFF6C6C6C);
        g.drawCenteredString(font, "提交", btnX + 30, btnY + 5, OmniTheme.TEXT_WHITE);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        OmnisearchMod.LOGGER.debug("keyPressed kc={} focused={}", kc, sb != null ? sb.getEditBox().isFocused() : "null-sb");
        if (kc == 256) { Minecraft.getInstance().setScreen(null); return true; }
        if (kc == 292) { // F6 - clear all caches and reload current page
            repo.clearCache();
            if (imageManager != null) {
                imageManager.clearCache();
            }
            clearCacheFlashTicks = 60;
            OmnisearchMod.LOGGER.info("[OmniScreen] Cache cleared via F6, reloading current page");
            // Reload current detail page from network if in detail view
            if (uiState.search().currentView() == SearchSessionState.BodyView.DETAIL
                    && uiState.search().selectedResultIndex() >= 0
                    && uiState.search().selectedResultIndex() < uiState.search().results().size()) {
                loadDetail(uiState.search().selectedResultIndex());
            } else {
                uiState = uiState.withDetail(uiState.detail().clearLayoutCache());
            }
            return true;
        }
        if (kc == 257 || kc == 335) {
            if (uiState.window().loading() == SearchState.LoadingState.CAPTCHA_REQUIRED) {
                submitCaptchaAnswer();
                return true;
            }
            submitSearch();
            return true;
        }
        if (uiState.window().loading() == SearchState.LoadingState.CAPTCHA_REQUIRED) {
            if (captchaInput.keyPressed(kc, sc, mod)) return true;
            return super.keyPressed(kc, sc, mod);
        }
        if (sb.getEditBox().keyPressed(kc, sc, mod)) {
            uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.QueryChanged(sb.getEditBox().getValue()));
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (uiState.window().loading() == SearchState.LoadingState.CAPTCHA_REQUIRED) {
            if (captchaInput.charTyped(codePoint, modifiers)) return true;
            return super.charTyped(codePoint, modifiers);
        }
        if (sb.getEditBox().charTyped(codePoint, modifiers)) {
            uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.QueryChanged(sb.getEditBox().getValue()));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        FloatingSearchWindow.Bounds panelBounds = floatingWindow.computeBounds(width, height);
        int searchBarHeight = sb.getTotalHeight();
        int[] searchBounds = floatingWindow.getSearchBarBounds(panelBounds, searchBarHeight);
        // Check captcha image click — open URL in browser
        if (uiState.window().loading() == SearchState.LoadingState.CAPTCHA_REQUIRED && uiState.window().captcha() != null) {
            int dx = panelBounds.x() + (panelBounds.width() - cd.getDialogWidth()) / 2;
            int dy = panelBounds.y() + Math.max(12, (panelBounds.height() - cd.computeDialogHeight()) / 2);
            int[] imgBounds = cd.getImageBounds(dx, dy);
            if (mx >= imgBounds[0] && mx <= imgBounds[0] + imgBounds[2]
                    && my >= imgBounds[1] && my <= imgBounds[1] + imgBounds[3]) {
                Util.getPlatform().openUri(uiState.window().captcha().captchaImageUrl());
                return true;
            }
            // Check submit button click (centered below the input field)
            int[] ib = cd.getImageBounds(dx, dy);
            int sbInputY = ib[1] + ib[3] + 14 + 18 + 4;
            int sbBtnX = dx + 120 - 30;
            if (mx >= sbBtnX && mx <= sbBtnX + 59
                    && my >= sbInputY && my <= sbInputY + 19) {
                submitCaptchaAnswer();
                return true;
            }
            // Focus captcha input on click
            captchaInput.setFocused(mx >= captchaInput.getX() && mx <= captchaInput.getX() + captchaInput.getWidth()
                    && my >= captchaInput.getY() && my <= captchaInput.getY() + captchaInput.getHeight());
            return super.mouseClicked(mx, my, btn);
        }

        boolean clickedSearch = mx >= searchBounds[0] && mx <= searchBounds[0] + searchBounds[2]
                && my >= searchBounds[1] && my <= searchBounds[1] + searchBounds[3];
        sb.getEditBox().setFocused(clickedSearch);
        // Check X button on mod filter tag
        if (uiState.search().modFilter() != null && sb.isXButtonClicked((int) mx, (int) my, searchBounds[1])) {
            uiState = uiState.withSearch(uiState.search().clearModFilter());
            return true;
        }
        if (clickedSearch && sb.getEditBox().mouseClicked(mx, my, btn)) {
            return true;
        }

        if (uiState.search().currentView() == SearchSessionState.BodyView.RESULTS) {
            int[] bodyBounds = floatingWindow.getBodyBounds(panelBounds, searchBarHeight);
            var click = resultsPane.handleClick(uiState.search(), bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3], mx, my);
            uiState = uiState.withSearch(click.state());
            if (click.modFilter() != null) {
                uiState = uiState.withSearch(uiState.search().applyModFilter(click.modFilter()));
                return true;
            }
            if (click.handled()) {
                loadDetail(click.row());
                return true;
            }
        }
        if (uiState.search().currentView() == SearchSessionState.BodyView.DETAIL) {
            int[] bodyBounds = floatingWindow.getBodyBounds(panelBounds, searchBarHeight);
            var click = detailPane.handleClick(uiState.detail(), bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3], mx, my);
            uiState = uiState.withDetail(click.state());
            if (click.goBack()) {
                uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.GoBack());
                invalidateDetailRequest();
                return true;
            }
            if (click.openUrl() != null) {
                Util.getPlatform().openUri(click.openUrl());
                return true;
            }
            if (click.handled()) {
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && uiState.search().currentView() == SearchSessionState.BodyView.DETAIL) {
            FloatingSearchWindow.Bounds panelBounds = floatingWindow.computeBounds(width, height);
            int[] bodyBounds = floatingWindow.getBodyBounds(panelBounds, sb.getTotalHeight());
            var nextDetail = detailPane.handleDrag(uiState.detail(), bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3], my);
            if (nextDetail != uiState.detail()) {
                uiState = uiState.withDetail(nextDetail);
                return true;
            }
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) {
            uiState = uiState.withDetail(detailPane.stopDragging(uiState.detail()));
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (uiState.search().currentView() == SearchSessionState.BodyView.RESULTS) {
            uiState = uiState.withSearch(resultsPane.handleScroll(uiState.search(), scrollY, height));
            // Auto-load more when scrolling near bottom
            var s = uiState.search();
            if (s.hasMore() && !s.loadingMore() && !s.results().isEmpty()) {
                int visibleRows = Math.max(1, height / 16);
                int nearBottomThreshold = Math.max(1, visibleRows / 2);
                if (s.resultsScrollOffset() + visibleRows >= s.results().size() - nearBottomThreshold) {
                    loadMoreResults();
                }
            }
            return true;
        }
        if (uiState.search().currentView() == SearchSessionState.BodyView.DETAIL) {
            uiState = uiState.withDetail(detailPane.handleScroll(uiState.detail(), scrollY, height));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        searchSeq++;
        detailSeq++;
        cancelOp(searchOp);
        cancelOp(detailOp);
        closeCaptchaImage();
        if (imageManager != null) {
            imageManager.close();
            imageManager = null;
        }
        super.removed();
    }

    private void closeCaptchaImage() {
        if (captchaImage != null) {
            captchaImage.close();
            captchaImage = null;
        }
    }

    private void loadMoreResults() {
        var s = uiState.search();
        int nextPage = s.currentPage() + 1;
        uiState = uiState.withSearch(s.withLoadingMore(true));
        long requestId = ++searchSeq;
        repo.searchMore(s.query(), nextPage)
            .thenAccept(results -> Minecraft.getInstance().tell(() -> {
                if (requestId != searchSeq) return;
                OmnisearchMod.LOGGER.debug("loadMoreResults page={} got {} results", nextPage, results.size());
                uiState = OmnisearchWindowReducer.reduce(
                    uiState,
                    new SearchEvent.MoreResultsLoaded(results)
                );
            }))
            .exceptionally(ex -> {
                Minecraft.getInstance().tell(() -> {
                    if (requestId != searchSeq) return;
                    OmnisearchMod.LOGGER.warn("loadMoreResults failed", ex);
                    uiState = uiState.withSearch(uiState.search().withLoadingMore(false));
                });
                return null;
            });
    }

    private void submitSearch() {
        slide.startSlideIn();
        OmnisearchMod.LOGGER.debug("submitSearch called, query={}", uiState.search().query().text());
        uiState = uiState.withSearch(uiState.search().withResultsScrollOffset(0));
        uiState = uiState.withDetail(uiState.detail().withScrollOffset(0));
        SearchQuery submittedQuery = uiState.search().query();
        long requestId = ++searchSeq;
        cancelOp(searchOp);
        invalidateDetailRequest();
        uiState = OmnisearchWindowReducer.reduce(
            OmnisearchWindowReducer.withPendingRequest(uiState, new PendingRequest.Search(submittedQuery)),
            new SearchEvent.SearchSubmitted()
        );

        searchOp = repo.search(submittedQuery)
            .thenAccept(results -> Minecraft.getInstance().tell(() -> {
                if (requestId != searchSeq || !submittedQuery.equals(uiState.search().query())) return;
                uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.SearchResultsLoaded(results));
                // Auto-navigate to detail when exactly 1 result (common for item-name search)
                if (results.size() == 1) {
                    loadDetail(0);
                }
            }))
            .exceptionally(ex -> {
                Minecraft.getInstance().tell(() -> {
                    if (requestId != searchSeq || !submittedQuery.equals(uiState.search().query())) return;
                    handleError(ex);
                });
                return null;
            });
    }

    private void submitCaptchaAnswer() {
        String answer = captchaInput.getValue();
        if (answer == null || answer.isBlank()) return;
        CaptchaContext captcha = uiState.window().captcha();
        if (captcha == null) return;
        PendingRequest pending = uiState.window().pendingRequest();
        if (pending == null) return;
        OmnisearchMod.LOGGER.info("Submitting captcha answer: '{}' to {}", answer, captcha.answerUrl());
        captchaInput.setValue("");
        closeCaptchaImage();

        // Clear captcha state and show loading
        uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.CaptchaSolved(answer));
        resumePendingRequest(pending, captcha, answer);
    }

    private void loadDetail(int row) {
        slide.startSlideIn();
        OmnisearchMod.LOGGER.debug("loadDetail called row={} id={}", row, row >= 0 && row < uiState.search().results().size() ? uiState.search().results().get(row).id() : "?");
        uiState = uiState.withDetail(uiState.detail().withScrollOffset(0).clearLayoutCache());
        uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.ResultSelected(row));
        String pageId = uiState.search().results().get(row).id();
        uiState = OmnisearchWindowReducer.withPendingRequest(uiState, new PendingRequest.Detail(pageId));
        long requestId = ++detailSeq;
        cancelOp(detailOp);

        detailOp = repo.getPage(pageId)
            .thenAccept(page -> Minecraft.getInstance().tell(() -> {
                if (requestId != detailSeq) return;
                uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.DetailLoaded(page));
            }))
            .exceptionally(ex -> {
                Minecraft.getInstance().tell(() -> {
                    if (requestId != detailSeq) return;
                    handleError(ex);
                });
                return null;
            });
    }

    private void invalidateDetailRequest() {
        detailSeq++;
        cancelOp(detailOp);
        uiState = uiState.withDetail(uiState.detail().clearLayoutCache());
    }

    private void resumePendingRequest(PendingRequest pending, CaptchaContext captcha, String answer) {
        switch (pending) {
            case PendingRequest.Search ignored -> {
                long requestId = ++searchSeq;
                cancelOp(searchOp);
                invalidateDetailRequest();
                searchOp = repo.resumeAfterCaptcha(pending, captcha, answer)
                    .thenAccept(result -> Minecraft.getInstance().tell(() -> {
                        if (requestId != searchSeq) return;
                        applyPendingResult(result);
                    }))
                    .exceptionally(ex -> {
                        Minecraft.getInstance().tell(() -> {
                            if (requestId != searchSeq) return;
                            handleError(ex);
                        });
                        return null;
                    });
            }
            case PendingRequest.Detail ignored -> {
                long requestId = ++detailSeq;
                cancelOp(detailOp);
                detailOp = repo.resumeAfterCaptcha(pending, captcha, answer)
                    .thenAccept(result -> Minecraft.getInstance().tell(() -> {
                        if (requestId != detailSeq) return;
                        applyPendingResult(result);
                    }))
                    .exceptionally(ex -> {
                        Minecraft.getInstance().tell(() -> {
                            if (requestId != detailSeq) return;
                            handleError(ex);
                        });
                        return null;
                    });
            }
        }
    }

    private void applyPendingResult(PendingRequestResult result) {
        switch (result) {
            case PendingRequestResult.SearchResults searchResults ->
                uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.SearchResultsLoaded(searchResults.results()));
            case PendingRequestResult.DetailPage detailPage ->
                uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.DetailLoaded(detailPage.page()));
        }
    }

    private void handleError(Throwable ex) {
        Throwable cause = ex;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof CaptchaRequiredException cre) {
            OmnisearchMod.LOGGER.info("CAPTCHA required, showing dialog");
            closeCaptchaImage();
            captchaImage = CaptchaImageRenderer.fromDataUri(cre.getCaptchaContext().captchaImageUrl());
            uiState = uiState.withWindow(
                uiState.window()
                    .withCaptcha(cre.getCaptchaContext())
                    .withLoading(SearchState.LoadingState.CAPTCHA_REQUIRED)
            );
        } else {
            OmnisearchMod.LOGGER.error("Unhandled search error: {}", cause.getMessage());
            uiState = OmnisearchWindowReducer.reduce(uiState, new SearchEvent.ErrorOccurred(cause.getMessage()));
        }
    }

    private static void cancelOp(CompletableFuture<?> op) {
        if (op != null && !op.isDone()) {
            op.cancel(true);
        }
    }
}
