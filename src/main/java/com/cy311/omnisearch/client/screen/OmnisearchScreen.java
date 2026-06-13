package com.cy311.omnisearch.client.screen;

import com.cy311.omnisearch.client.render.*;
import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.data.repository.SearchRepository;
import com.cy311.omnisearch.data.source.CaptchaRequiredException;
import com.cy311.omnisearch.search.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// verified: Screen, Minecraft.setScreen(), GuiGraphics — standard MC API, stable
public class OmnisearchScreen extends Screen {
    private SearchState state;
    private final SearchRepository repo;
    private SearchBarWidget sb;
    private ResultListWidget rl;
    private DetailPanelWidget dp;
    private CaptchaDialogWidget cd;
    private CompletableFuture<?> op;
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
            case CAPTCHA_REQUIRED -> { if (state.captcha() != null) cd.render(g, width / 2 - 120, height / 2 - 80, state.captcha()); }
        }
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) { Minecraft.getInstance().setScreen(null); return true; }
        if (kc == 257 || kc == 335) {
            state = SearchReducer.reduce(state, new SearchEvent.SearchSubmitted());
            op = repo.search(state.query()).thenAccept(r -> Minecraft.getInstance().tell(() ->
                state = SearchReducer.reduce(state, new SearchEvent.SearchResultsLoaded(r))
            )).exceptionally(ex -> { Minecraft.getInstance().tell(() -> state = onError(ex)); return null; });
            return true;
        }
        if (sb.getEditBox().keyPressed(kc, sc, mod)) {
            state = SearchReducer.reduce(state, new SearchEvent.QueryChanged(sb.getEditBox().getValue()));
            return true;
        }
        // Unhandled keys: delegate to parent Screen (no-op for most keys)
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (state.currentPage() == SearchState.Page.RESULTS) {
            int ly = 10 + sb.getEditBox().getHeight() + 16;
            int row = rl.getRowAt((int) my, ly, 0);
            if (row >= 0 && row < state.results().size()) {
                state = SearchReducer.reduce(state, new SearchEvent.ResultSelected(row));
                var id = state.results().get(row).id();
                op = repo.getPage(id).thenAccept(p -> Minecraft.getInstance().tell(() ->
                    state = SearchReducer.reduce(state, new SearchEvent.DetailLoaded(p))
                )).exceptionally(ex -> { Minecraft.getInstance().tell(() -> state = onError(ex)); return null; });
                return true;
            }
        }
        if (state.currentPage() == SearchState.Page.DETAIL && mx >= 6 && mx <= 26 && my >= 5 && my <= 25) {
            state = SearchReducer.reduce(state, new SearchEvent.GoBack());
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private SearchState onError(Throwable ex) {
        if (ex instanceof CompletionException ce && ce.getCause() instanceof CaptchaRequiredException cre)
            return state.withCaptcha(cre.getCaptchaContext()).withLoading(SearchState.LoadingState.CAPTCHA_REQUIRED);
        return SearchReducer.reduce(state, new SearchEvent.ErrorOccurred(ex.getMessage()));
    }
}
