package com.cy311.omnisearch.client.screen;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.client.render.*;
import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.client.render.document.PreparedDocumentLayout;
import com.cy311.omnisearch.client.render.image.ImageManager;
import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.PendingRequest;
import com.cy311.omnisearch.data.model.PendingRequestResult;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.repository.SearchRepository;
import com.cy311.omnisearch.data.source.CaptchaRequiredException;
import com.cy311.omnisearch.gui.animation.SlideAnimation;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import com.cy311.omnisearch.search.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.io.FileWriter;
import java.io.PrintWriter;

// verified: Screen, Minecraft.setScreen(), GuiGraphics — standard MC API, stable
// verified: EditBox from NeoForge 1.21.1 lexxie.dev 2026-06-14
// verified: Util.NULL from NeoForge 1.21.1 2026-06-14
public class OmnisearchScreen extends Screen {
    private SearchState state;
    private final SearchRepository repo;
    private SearchBarWidget sb;
    private ResultListWidget rl;
    private DetailPanelWidget dp;
    private CaptchaDialogWidget cd;
    private EditBox captchaInput;
    private CaptchaImageRenderer captchaImage;
    private ImageManager imageManager;
    private final java.util.function.Function<String, byte[]> imageDownloader;
    private long searchSeq;
    private long detailSeq;
    private CompletableFuture<?> searchOp;
    private CompletableFuture<?> detailOp;
    private int resultsScrollOffset;
    private int detailScrollOffset;
    private DocumentRenderer detailDocRenderer;
    private String cachedDetailPageId;
    private int cachedDetailWidth;
    private PreparedDocumentLayout cachedDetailLayout;
    private java.util.List<DocumentRenderer.LinkHit> cachedDetailLinks;
    private boolean detailDraggingScrollbar;
    private int detailContentHeight;
    private final SlideAnimation slide = new SlideAnimation();
    // Clear cache flash message
    private int clearCacheFlashTicks = 0;
    private static final int SBW = 300;

    private static void debugLog(String msg) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("omnisearch-debug.log", true))) {
            pw.println(System.currentTimeMillis() + " " + msg);
        } catch (Exception ignored) {}
    }

    public OmnisearchScreen(SearchRepository repo, java.util.function.Function<String, byte[]> imageDownloader) {
        super(Component.literal("Omnisearch"));
        debugLog("Screen constructor called");
        this.repo = repo;
        this.imageDownloader = imageDownloader;
        this.state = SearchState.initial();
    }

    @Override
    protected void init() {
        super.init();
        debugLog("Screen init() called, width=" + width + " height=" + height);
        imageManager = new ImageManager(imageDownloader != null ? imageDownloader : url -> null);
        detailDocRenderer = new DocumentRenderer(font, imageManager);
        int cx = (width - SBW) / 2;
        sb = new SearchBarWidget(font, cx, height / 3, SBW);
        rl = new ResultListWidget(font);
        dp = new DetailPanelWidget(font);
        cd = new CaptchaDialogWidget(font);
        // Captcha input EditBox (hidden until captcha is required)
        captchaInput = new EditBox(font, width / 2 - 120 + 9, 0, 222, 18, Component.literal(""));
        sb.getEditBox().setFocused(true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        switch (state.currentPage()) {
            case SEARCH -> sb.render(g, (width - SBW) / 2, height / 3, SBW, state.query().text());
            case RESULTS -> {
                g.pose().pushPose();
                slide.applyTransform(g, width);
                sb.render(g, (width - SBW) / 2, 10, SBW, state.query().text());
                int ly = 10 + sb.getEditBox().getHeight() + 16;
                int listH = height - ly - 10;
                rl.render(g, 20, ly, width - 40, listH, state.results(), -1, resultsScrollOffset, mx, my);
                // Clamp scroll offset after render (which tells us total height)
                int maxScroll = Math.max(0, state.results().size() - Math.max(1, listH / 20));
                resultsScrollOffset = Math.max(0, Math.min(resultsScrollOffset, maxScroll));
                g.pose().popPose();
            }
            case DETAIL -> {
                if (state.detailPage() != null) {
                    g.pose().pushPose();
                    slide.applyTransform(g, width);
                    dp.render(g, 0, 0, width, height, state.detailPage());
                    int[] ca = dp.getContentAreaBounds(0, 0, width, height);
                    // Clamp detail scroll
                    detailScrollOffset = Math.max(0, detailScrollOffset);
                    // Refresh cached layout if page or width changed
                    ensureDetailLayout(state.detailPage(), ca[2]);
                    g.enableScissor(ca[0], ca[1], ca[0] + ca[2], ca[1] + ca[3]);
                    detailDocRenderer.paint(g, cachedDetailLayout, ca[0], ca[1] - detailScrollOffset);
                    g.disableScissor();
                    // Clamp scroll to max using local height
                    detailContentHeight = cachedDetailLayout.height();
                    int maxScroll = Math.max(0, detailContentHeight - ca[3]);
                    detailScrollOffset = Math.min(detailScrollOffset, maxScroll);
                    // Draw scrollbar
                    if (maxScroll > 0) {
                        drawDetailScrollbar(g, ca, maxScroll);
                    }
                    g.pose().popPose();
                }
            }
        }
        switch (state.loading()) {
            case IDLE -> {}
            case LOADING -> g.drawCenteredString(font, "Searching...", width / 2, height / 2, OmniTheme.TEXT_WHITE);
            case ERROR -> g.drawCenteredString(font, state.errorMessage(), width / 2, height / 2, OmniTheme.TEXT_ERROR);
            case CAPTCHA_REQUIRED -> renderCaptcha(g, state.captcha(), mx, my, d);
        }
        // Cache-clear flash message
        if (clearCacheFlashTicks > 0) {
            g.drawCenteredString(font, "缓存已清除 (F6)", width / 2, 50, 0xFF55FF55);
            clearCacheFlashTicks--;
        }
    }

    private void renderCaptcha(GuiGraphics g, CaptchaContext captcha, int mx, int my, float d) {
        if (captcha == null) return;
        int dx = width / 2 - 120;
        int dy = height / 2 - 80;
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
        debugLog("keyPressed kc=" + kc + " focused=" + (sb != null ? sb.getEditBox().isFocused() : "null-sb"));
        if (kc == 256) { Minecraft.getInstance().setScreen(null); return true; }
        if (kc == 292) { // F6 — clear cache and force refresh
            repo.clearCache();
            cachedDetailPageId = null;
            clearCacheFlashTicks = 60;
            OmnisearchMod.LOGGER.info("[OmniScreen] Cache cleared via F6");
            return true;
        }
        if (kc == 257 || kc == 335) {
            if (state.loading() == SearchState.LoadingState.CAPTCHA_REQUIRED) {
                submitCaptchaAnswer();
                return true;
            }
            submitSearch();
            return true;
        }
        if (state.loading() == SearchState.LoadingState.CAPTCHA_REQUIRED) {
            if (captchaInput.keyPressed(kc, sc, mod)) return true;
            return super.keyPressed(kc, sc, mod);
        }
        if (sb.getEditBox().keyPressed(kc, sc, mod)) {
            state = SearchReducer.reduce(state, new SearchEvent.QueryChanged(sb.getEditBox().getValue()));
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (state.loading() == SearchState.LoadingState.CAPTCHA_REQUIRED) {
            if (captchaInput.charTyped(codePoint, modifiers)) return true;
            return super.charTyped(codePoint, modifiers);
        }
        if (sb.getEditBox().charTyped(codePoint, modifiers)) {
            state = SearchReducer.reduce(state, new SearchEvent.QueryChanged(sb.getEditBox().getValue()));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Check captcha image click — open URL in browser
        if (state.loading() == SearchState.LoadingState.CAPTCHA_REQUIRED && state.captcha() != null) {
            int dx = width / 2 - 120;
            int dy = height / 2 - 80;
            int[] imgBounds = cd.getImageBounds(dx, dy);
            if (mx >= imgBounds[0] && mx <= imgBounds[0] + imgBounds[2]
                    && my >= imgBounds[1] && my <= imgBounds[1] + imgBounds[3]) {
                Util.getPlatform().openUri(state.captcha().captchaImageUrl());
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

        if (state.currentPage() == SearchState.Page.RESULTS) {
            int ly = 10 + sb.getEditBox().getHeight() + 16;
            int row = rl.getRowAt((int) my, ly, resultsScrollOffset);
            if (row >= 0 && row < state.results().size()) {
                loadDetail(row);
                return true;
            }
        }
        if (state.currentPage() == SearchState.Page.DETAIL && mx >= 6 && mx <= 24 && my >= 5 && my <= 23) {
            state = SearchReducer.reduce(state, new SearchEvent.GoBack());
            invalidateDetailRequest();
            cachedDetailPageId = null;
            return true;
        }
        if (state.currentPage() == SearchState.Page.DETAIL && state.detailPage() != null) {
            // Check title click (opens page source URL)
            if (dp.getTitleUrl() != null) {
                int[] title = dp.getTitleClickTarget();
                if (title != null && mx >= title[0] && mx <= title[0] + title[2]
                        && my >= title[1] && my <= title[1] + title[3]) {
                    Util.getPlatform().openUri(dp.getTitleUrl());
                    return true;
                }
            }
            // Check source mod tag click
            if (dp.getTagUrl() != null) {
                int[] tag = dp.getTagClickTarget();
                if (tag != null && mx >= tag[0] && mx <= tag[0] + tag[2]
                        && my >= tag[1] && my <= tag[1] + tag[3]) {
                    Util.getPlatform().openUri(dp.getTagUrl());
                    return true;
                }
            }
            // Check link clicks in the rendered document
            int[] ca = dp.getContentAreaBounds(0, 0, width, height);
            if (cachedDetailLinks != null) {
                for (var link : cachedDetailLinks) {
                    if (mx >= ca[0] + link.x() && mx <= ca[0] + link.x() + link.w()
                            && my >= ca[1] + link.y() - detailScrollOffset
                            && my <= ca[1] + link.y() + link.h() - detailScrollOffset) {
                        Util.getPlatform().openUri(link.url());
                        return true;
                    }
                }
            }
            // Check scrollbar click
            int scrollbarX = ca[0] + ca[2] - OmniTheme.SCROLLBAR_WIDTH;
            if (mx >= scrollbarX && mx <= scrollbarX + OmniTheme.SCROLLBAR_WIDTH
                    && my >= ca[1] && my <= ca[1] + ca[3]) {
                detailDraggingScrollbar = true;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && detailDraggingScrollbar && state.currentPage() == SearchState.Page.DETAIL) {
            int[] ca = dp.getContentAreaBounds(0, 0, width, height);
            int maxScroll = Math.max(1, detailContentHeight - ca[3]);
            float thumbRatio = Math.min(1f, (float) ca[3] / Math.max(1, detailContentHeight));
            int thumbHeight = Math.max(8, (int) (ca[3] * thumbRatio));
            float fraction = (float) (my - ca[1]) / (ca[3] - thumbHeight);
            fraction = Math.max(0, Math.min(1, fraction));
            detailScrollOffset = (int) (fraction * maxScroll);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) detailDraggingScrollbar = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (state.currentPage() == SearchState.Page.RESULTS) {
            resultsScrollOffset -= (int) Math.round(scrollY) * 3;
            return true;
        }
        if (state.currentPage() == SearchState.Page.DETAIL) {
            detailScrollOffset -= (int) Math.round(scrollY) * 20;
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

    private void submitSearch() {
        slide.startSlideIn();
        debugLog("submitSearch called, query=" + state.query().text());
        resultsScrollOffset = 0;
        detailScrollOffset = 0;
        SearchQuery submittedQuery = state.query();
        long requestId = ++searchSeq;
        cancelOp(searchOp);
        invalidateDetailRequest();
        state = SearchReducer.reduce(
            state.withPendingRequest(new PendingRequest.Search(submittedQuery)),
            new SearchEvent.SearchSubmitted()
        );

        searchOp = repo.search(submittedQuery)
            .thenAccept(results -> Minecraft.getInstance().tell(() -> {
                if (requestId != searchSeq || !submittedQuery.equals(state.query())) return;
                state = SearchReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results));
            }))
            .exceptionally(ex -> {
                Minecraft.getInstance().tell(() -> {
                    if (requestId != searchSeq || !submittedQuery.equals(state.query())) return;
                    handleError(ex);
                });
                return null;
            });
    }

    private void submitCaptchaAnswer() {
        String answer = captchaInput.getValue();
        if (answer == null || answer.isBlank()) return;
        CaptchaContext captcha = state.captcha();
        if (captcha == null) return;
        PendingRequest pending = state.pendingRequest();
        if (pending == null) return;
        OmnisearchMod.LOGGER.info("Submitting captcha answer: '{}' to {}", answer, captcha.answerUrl());
        captchaInput.setValue("");
        closeCaptchaImage();

        // Clear captcha state and show loading
        state = SearchReducer.reduce(state, new SearchEvent.CaptchaSolved(answer));
        resumePendingRequest(pending, captcha, answer);
    }

    private void loadDetail(int row) {
        slide.startSlideIn();
        debugLog("loadDetail called row=" + row + " id=" + (row >= 0 && row < state.results().size() ? state.results().get(row).id() : "?"));
        detailScrollOffset = 0;
        cachedDetailPageId = null;
        state = SearchReducer.reduce(state, new SearchEvent.ResultSelected(row));
        String pageId = state.results().get(row).id();
        state = state.withPendingRequest(new PendingRequest.Detail(pageId));
        long requestId = ++detailSeq;
        cancelOp(detailOp);

        detailOp = repo.getPage(pageId)
            .thenAccept(page -> Minecraft.getInstance().tell(() -> {
                if (requestId != detailSeq) return;
                state = SearchReducer.reduce(state, new SearchEvent.DetailLoaded(page));
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
        cachedDetailPageId = null;
    }

    private void ensureDetailLayout(com.cy311.omnisearch.data.model.ItemPage page, int width) {
        if (cachedDetailPageId != null && cachedDetailPageId.equals(page.id()) && cachedDetailWidth == width) {
            return; // cache hit
        }
        cachedDetailLayout = detailDocRenderer.prepare(page.document(), width);
        cachedDetailLinks = cachedDetailLayout.extractLinks();
        cachedDetailPageId = page.id();
        cachedDetailWidth = width;
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
                state = SearchReducer.reduce(state, new SearchEvent.SearchResultsLoaded(searchResults.results()));
            case PendingRequestResult.DetailPage detailPage ->
                state = SearchReducer.reduce(state, new SearchEvent.DetailLoaded(detailPage.page()));
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
            state = state.withCaptcha(cre.getCaptchaContext()).withLoading(SearchState.LoadingState.CAPTCHA_REQUIRED);
        } else {
            OmnisearchMod.LOGGER.error("Unhandled search error: {}", cause.getMessage());
            state = SearchReducer.reduce(state, new SearchEvent.ErrorOccurred(cause.getMessage()));
        }
    }

    private static void cancelOp(CompletableFuture<?> op) {
        if (op != null && !op.isDone()) {
            op.cancel(true);
        }
    }

    private void drawDetailScrollbar(GuiGraphics g, int[] ca, int maxScroll) {
        int sx = ca[0] + ca[2] - OmniTheme.SCROLLBAR_WIDTH;
        g.fill(sx, ca[1], sx + OmniTheme.SCROLLBAR_WIDTH, ca[1] + ca[3], OmniTheme.BG_SCROLLBAR_TRACK);
        float thumbRatio = Math.min(1f, (float) ca[3] / Math.max(1, detailContentHeight));
        int thumbH = Math.max(8, (int) (ca[3] * thumbRatio));
        float frac = (float) detailScrollOffset / Math.max(1, maxScroll);
        int thumbY = ca[1] + (int) ((ca[3] - thumbH) * Math.min(1, Math.max(0, frac)));
        g.fill(sx + 1, thumbY, sx + OmniTheme.SCROLLBAR_WIDTH - 1, thumbY + thumbH, OmniTheme.BG_SCROLLBAR_THUMB);
    }
}
