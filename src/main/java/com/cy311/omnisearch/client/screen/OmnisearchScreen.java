package com.cy311.omnisearch.client.screen;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.client.render.*;
import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.SearchQuery;
import com.cy311.omnisearch.data.repository.SearchRepository;
import com.cy311.omnisearch.data.source.CaptchaRequiredException;
import com.cy311.omnisearch.search.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
    private long searchSeq;
    private long detailSeq;
    private CompletableFuture<?> searchOp;
    private CompletableFuture<?> detailOp;
    private static final int SBW = 300;

    public OmnisearchScreen(SearchRepository repo) {
        super(Component.literal("Omnisearch"));
        this.repo = repo;
        this.state = SearchState.initial();
    }

    @Override
    protected void init() {
        super.init();
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
                sb.render(g, (width - SBW) / 2, 10, SBW, state.query().text());
                int ly = 10 + sb.getEditBox().getHeight() + 16;
                rl.render(g, 20, ly, width - 40, height - ly - 10, state.results(), -1, 0);
            }
            case DETAIL -> {
                if (state.detailPage() != null) {
                    dp.render(g, 0, 0, width, height, state.detailPage());
                    int[] ca = dp.getContentAreaBounds(0, 0, width, height);
                    new DocumentRenderer(g, font, ca[0], ca[1], ca[2]).render(state.detailPage().document());
                }
            }
        }
        switch (state.loading()) {
            case LOADING -> g.drawCenteredString(font, "Searching...", width / 2, height / 2, 0xFFFFFFFF);
            case ERROR -> g.drawCenteredString(font, state.errorMessage(), width / 2, height / 2, 0xFFFF5555);
            case CAPTCHA_REQUIRED -> renderCaptcha(g, state.captcha(), mx, my, d);
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
        g.drawCenteredString(font, captcha.captchaId(), dx + 120, cy + ch + 2, 0xFFFFAA00);

        // Input field background & border
        int inputY = cy + ch + 14;
        int inputW = cw;
        g.fill(cx, inputY, cx + inputW, inputY + 18, 0xFF000000);
        g.hLine(cx, cx + inputW - 1, inputY, 0xFF373737);
        g.vLine(cx, inputY, inputY + 17, 0xFF373737);
        g.hLine(cx, cx + inputW - 1, inputY + 17, 0xFFFFFFFF);
        g.vLine(cx + inputW - 1, inputY, inputY + 17, 0xFFFFFFFF);

        // EditBox for captcha answer
        captchaInput.setX(cx + 1);
        captchaInput.setY(inputY + 1);
        captchaInput.setWidth(inputW - 2);
        captchaInput.render(g, mx, my, d);

        // Submit button
        int btnX = dx + 120 - 30;
        int btnY = inputY + 18 + 4;
        g.hLine(btnX, btnX + 59, btnY, 0xFFFFFFFF);
        g.vLine(btnX, btnY, btnY + 19, 0xFFFFFFFF);
        g.hLine(btnX, btnX + 59, btnY + 19, 0xFF373737);
        g.vLine(btnX + 59, btnY, btnY + 19, 0xFF373737);
        g.fill(btnX + 1, btnY + 1, btnX + 59, btnY + 19, 0xFF6C6C6C);
        g.drawCenteredString(font, "提交", btnX + 30, btnY + 5, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) { Minecraft.getInstance().setScreen(null); return true; }
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
            int row = rl.getRowAt((int) my, ly, 0);
            if (row >= 0 && row < state.results().size()) {
                loadDetail(row);
                return true;
            }
        }
        if (state.currentPage() == SearchState.Page.DETAIL && mx >= 6 && mx <= 26 && my >= 5 && my <= 25) {
            state = SearchReducer.reduce(state, new SearchEvent.GoBack());
            invalidateDetailRequest();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void removed() {
        searchSeq++;
        detailSeq++;
        cancelOp(searchOp);
        cancelOp(detailOp);
        closeCaptchaImage();
        super.removed();
    }

    private void closeCaptchaImage() {
        if (captchaImage != null) {
            captchaImage.close();
            captchaImage = null;
        }
    }

    private void submitSearch() {
        System.err.println("[omnisearch] submitSearch called, query=" + state.query().text());
        SearchQuery submittedQuery = state.query();
        long requestId = ++searchSeq;
        cancelOp(searchOp);
        invalidateDetailRequest();
        state = SearchReducer.reduce(state, new SearchEvent.SearchSubmitted());

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
        OmnisearchMod.LOGGER.info("Submitting captcha answer: '{}' to {}", answer, captcha.answerUrl());
        captchaInput.setValue("");
        closeCaptchaImage();

        // Clear captcha state and show loading
        state = SearchReducer.reduce(state, new SearchEvent.CaptchaSolved(answer));

        // Submit captcha answer and retry the original request
        long requestId = ++searchSeq;
        cancelOp(searchOp);
        invalidateDetailRequest();
        state = SearchReducer.reduce(state, new SearchEvent.SearchSubmitted());

        searchOp = repo.submitCaptcha(state.query(), captcha, answer)
            .thenAccept(results -> Minecraft.getInstance().tell(() -> {
                if (requestId != searchSeq) return;
                state = SearchReducer.reduce(state, new SearchEvent.SearchResultsLoaded(results));
            }))
            .exceptionally(ex -> {
                Minecraft.getInstance().tell(() -> {
                    if (requestId != searchSeq) return;
                    handleError(ex);
                });
                return null;
            });
    }

    private void loadDetail(int row) {
        state = SearchReducer.reduce(state, new SearchEvent.ResultSelected(row));
        String pageId = state.results().get(row).id();
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
}
