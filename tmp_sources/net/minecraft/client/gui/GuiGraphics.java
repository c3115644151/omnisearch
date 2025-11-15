package net.minecraft.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.gui.render.state.TiledBlitRenderState;
import net.minecraft.client.gui.render.state.pip.GuiBannerResultRenderState;
import net.minecraft.client.gui.render.state.pip.GuiBookModelRenderState;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.gui.render.state.pip.GuiProfilerChartRenderState;
import net.minecraft.client.gui.render.state.pip.GuiSignRenderState;
import net.minecraft.client.gui.render.state.pip.GuiSkinRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.model.BannerFlagModel;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector2ic;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class GuiGraphics implements net.neoforged.neoforge.client.extensions.IGuiGraphicsExtension {
    private static final int EXTRA_SPACE_AFTER_FIRST_TOOLTIP_LINE = 2;
    private final Minecraft minecraft;
    private final Matrix3x2fStack pose;
    private final GuiGraphics.ScissorStack scissorStack = new GuiGraphics.ScissorStack();
    private final MaterialSet materials;
    private final TextureAtlas guiSprites;
    private final GuiRenderState guiRenderState;
    private CursorType pendingCursor = CursorType.DEFAULT;
    @Nullable
    private Runnable deferredTooltip;
    private final List<GuiGraphics.OutlineBox> deferredOutlines = new ArrayList<>();
    private ItemStack tooltipStack = ItemStack.EMPTY;

    private GuiGraphics(Minecraft p_282144_, Matrix3x2fStack p_415937_, GuiRenderState p_415955_) {
        this.minecraft = p_282144_;
        this.pose = p_415937_;
        AtlasManager atlasmanager = p_282144_.getAtlasManager();
        this.materials = atlasmanager;
        this.guiSprites = atlasmanager.getAtlasOrThrow(AtlasIds.GUI);
        this.guiRenderState = p_415955_;
    }

    public GuiGraphics(Minecraft p_283406_, GuiRenderState p_416249_) {
        this(p_283406_, new Matrix3x2fStack(16), p_416249_);
    }

    public void requestCursor(CursorType p_443154_) {
        this.pendingCursor = p_443154_;
    }

    public void applyCursor(Window p_442894_) {
        p_442894_.selectCursor(this.pendingCursor);
    }

    public int guiWidth() {
        return this.minecraft.getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return this.minecraft.getWindow().getGuiScaledHeight();
    }

    public void nextStratum() {
        this.guiRenderState.nextStratum();
    }

    public void blurBeforeThisStratum() {
        this.guiRenderState.blurBeforeThisStratum();
    }

    public Matrix3x2fStack pose() {
        return this.pose;
    }

    /**
     * Draws a horizontal line from minX to maxX at the specified y-coordinate with the given color.
     *
     * @param minX  the x-coordinate of the start point.
     * @param maxX  the x-coordinate of the end point.
     * @param y     the y-coordinate of the line.
     * @param color the color of the line.
     */
    public void hLine(int minX, int maxX, int y, int color) {
        if (maxX < minX) {
            int i = minX;
            minX = maxX;
            maxX = i;
        }

        this.fill(minX, y, maxX + 1, y + 1, color);
    }

    /**
     * Draws a vertical line from minY to maxY at the specified x-coordinate with the given color.
     *
     * @param x     the x-coordinate of the line.
     * @param minY  the y-coordinate of the start point.
     * @param maxY  the y-coordinate of the end point.
     * @param color the color of the line.
     */
    public void vLine(int x, int minY, int maxY, int color) {
        if (maxY < minY) {
            int i = minY;
            minY = maxY;
            maxY = i;
        }

        this.fill(x, minY + 1, x + 1, maxY, color);
    }

    /**
     * Enables scissoring with the specified screen coordinates.
     *
     * @param minX the minimum x-coordinate of the scissor region.
     * @param minY the minimum y-coordinate of the scissor region.
     * @param maxX the maximum x-coordinate of the scissor region.
     * @param maxY the maximum y-coordinate of the scissor region.
     */
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        ScreenRectangle screenrectangle = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY)
            .transformAxisAligned(this.pose);
        this.scissorStack.push(screenrectangle);
    }

    public void disableScissor() {
        this.scissorStack.pop();
    }

    public boolean containsPointInScissor(int x, int y) {
        return this.scissorStack.containsPoint(x, y);
    }

    /**
     * Fills a rectangle with the specified color using the given coordinates as the boundaries.
     *
     * @param minX  the minimum x-coordinate of the rectangle.
     * @param minY  the minimum y-coordinate of the rectangle.
     * @param maxX  the maximum x-coordinate of the rectangle.
     * @param maxY  the maximum y-coordinate of the rectangle.
     * @param color the color to fill the rectangle with.
     */
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        this.fill(RenderPipelines.GUI, minX, minY, maxX, maxY, color);
    }

    public void fill(RenderPipeline p_416410_, int p_281437_, int p_283660_, int p_282606_, int p_283413_, int p_283428_) {
        if (p_281437_ < p_282606_) {
            int i = p_281437_;
            p_281437_ = p_282606_;
            p_282606_ = i;
        }

        if (p_283660_ < p_283413_) {
            int j = p_283660_;
            p_283660_ = p_283413_;
            p_283413_ = j;
        }

        this.submitColoredRectangle(p_416410_, TextureSetup.noTexture(), p_281437_, p_283660_, p_282606_, p_283413_, p_283428_, null);
    }

    /**
     * Fills a rectangle with a gradient color from colorFrom to colorTo using the given coordinates as the boundaries.
     *
     * @param x1        the x-coordinate of the first corner of the rectangle.
     * @param y1        the y-coordinate of the first corner of the rectangle.
     * @param x2        the x-coordinate of the second corner of the rectangle.
     * @param y2        the y-coordinate of the second corner of the rectangle.
     * @param colorFrom the starting color of the gradient.
     * @param colorTo   the ending color of the gradient.
     */
    public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        this.submitColoredRectangle(RenderPipelines.GUI, TextureSetup.noTexture(), x1, y1, x2, y2, colorFrom, colorTo);
    }

    public void fill(RenderPipeline p_416027_, TextureSetup p_415746_, int p_286234_, int p_286444_, int p_286244_, int p_286411_) {
        this.submitColoredRectangle(p_416027_, p_415746_, p_286234_, p_286444_, p_286244_, p_286411_, -1, null);
    }

    private void submitColoredRectangle(
        RenderPipeline p_416727_,
        TextureSetup p_416131_,
        int p_415712_,
        int p_416427_,
        int p_416376_,
        int p_415748_,
        int p_415666_,
        @Nullable Integer p_415938_
    ) {
        this.guiRenderState
            .submitGuiElement(
                new ColoredRectangleRenderState(
                    p_416727_,
                    p_416131_,
                    new Matrix3x2f(this.pose),
                    p_415712_,
                    p_416427_,
                    p_416376_,
                    p_415748_,
                    p_415666_,
                    p_415938_ != null ? p_415938_ : p_415666_,
                    this.scissorStack.peek()
                )
            );
    }

    public void textHighlight(int p_428831_, int p_428851_, int p_428846_, int p_428835_) {
        this.fill(RenderPipelines.GUI_INVERT, p_428831_, p_428851_, p_428846_, p_428835_, -1);
        this.fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, p_428831_, p_428851_, p_428846_, p_428835_, -16776961);
    }

    /**
     * Draws a centered string at the specified coordinates using the given font, text, and color.
     *
     * @param font  the font to use for rendering.
     * @param text  the text to draw.
     * @param x     the x-coordinate of the center of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        this.drawString(font, text, x - font.width(text) / 2, y, color);
    }

    /**
     * Draws a centered string at the specified coordinates using the given font, text component, and color.
     *
     * @param font  the font to use for rendering.
     * @param text  the text component to draw.
     * @param x     the x-coordinate of the center of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        FormattedCharSequence formattedcharsequence = text.getVisualOrderText();
        this.drawString(font, formattedcharsequence, x - font.width(formattedcharsequence) / 2, y, color);
    }

    /**
     * Draws a centered string at the specified coordinates using the given font, formatted character sequence, and color.
     *
     * @param font  the font to use for rendering.
     * @param text  the formatted character sequence to draw.
     * @param x     the x-coordinate of the center of the string.
     * @param y     the y-coordinate of the string.
     * @param color the color of the string.
     */
    public void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.drawString(font, text, x - font.width(text) / 2, y, color);
    }

    public void drawString(Font p_282003_, @Nullable String p_281403_, int p_282714_, int p_282041_, int p_281908_) {
        this.drawString(p_282003_, p_281403_, p_282714_, p_282041_, p_281908_, true);
    }

    public void drawString(Font p_283019_, @Nullable String p_415853_, int p_283379_, int p_283346_, int p_282119_, boolean p_416601_) {
        if (p_415853_ != null) {
            this.drawString(p_283019_, Language.getInstance().getVisualOrder(FormattedText.of(p_415853_)), p_283379_, p_283346_, p_282119_, p_416601_);
        }
    }

    public void drawString(Font p_281653_, FormattedCharSequence p_416271_, int p_283102_, int p_282347_, int p_281429_) {
        this.drawString(p_281653_, p_416271_, p_283102_, p_282347_, p_281429_, true);
    }

    public void drawString(Font p_283343_, FormattedCharSequence p_416388_, int p_283569_, int p_283418_, int p_281560_, boolean p_282130_) {
        if (ARGB.alpha(p_281560_) != 0) {
            this.guiRenderState
                .submitText(
                    new GuiTextRenderState(
                        p_283343_, p_416388_, new Matrix3x2f(this.pose), p_283569_, p_283418_, p_281560_, 0, p_282130_, this.scissorStack.peek()
                    )
                );
        }
    }

    public void drawString(Font p_281547_, Component p_282131_, int p_282857_, int p_281250_, int p_282195_) {
        this.drawString(p_281547_, p_282131_, p_282857_, p_281250_, p_282195_, true);
    }

    public void drawString(Font p_282636_, Component p_416319_, int p_281586_, int p_282816_, int p_281743_, boolean p_282394_) {
        this.drawString(p_282636_, p_416319_.getVisualOrderText(), p_281586_, p_282816_, p_281743_, p_282394_);
    }

    /**
     * Draws a formatted text with word wrapping at the specified coordinates using the given font, text, line width, and color.
     *
     * @param font      the font to use for rendering.
     * @param text      the formatted text to draw.
     * @param x         the x-coordinate of the starting position.
     * @param y         the y-coordinate of the starting position.
     * @param lineWidth the maximum width of each line before wrapping.
     * @param color     the color of the text.
     */
    public void drawWordWrap(Font font, FormattedText text, int x, int y, int lineWidth, int color) {
        this.drawWordWrap(font, text, x, y, lineWidth, color, true);
    }

    /**
     * Draws a formatted text with word wrapping at the specified coordinates using the given font, text, line width, and color.
     *
     * @param font      the font to use for rendering.
     * @param text      the formatted text to draw.
     * @param x         the x-coordinate of the starting position.
     * @param y         the y-coordinate of the starting position.
     * @param lineWidth the maximum width of each line before wrapping.
     * @param color     the color of the text.
     */
    public void drawWordWrap(Font font, FormattedText text, int x, int y, int lineWidth, int color, boolean p_383224_) {
        for (FormattedCharSequence formattedcharsequence : font.split(text, lineWidth)) {
            this.drawString(font, formattedcharsequence, x, y, color, p_383224_);
            y += 9;
        }
    }

    public void drawStringWithBackdrop(Font p_348650_, Component p_348614_, int p_348465_, int p_348495_, int p_348581_, int p_348666_) {
        int i = this.minecraft.options.getBackgroundColor(0.0F);
        if (i != 0) {
            int j = 2;
            this.fill(p_348465_ - 2, p_348495_ - 2, p_348465_ + p_348581_ + 2, p_348495_ + 9 + 2, ARGB.multiply(i, p_348666_));
        }

        this.drawString(p_348650_, p_348614_, p_348465_, p_348495_, p_348666_, true);
    }

    public void submitOutline(int p_437200_, int p_437425_, int p_437270_, int p_437412_, int p_437361_) {
        this.deferredOutlines.add(new GuiGraphics.OutlineBox(p_437200_, p_437425_, p_437270_, p_437412_, p_437361_));
    }

    public void blitSprite(RenderPipeline p_415703_, ResourceLocation p_294915_, int p_295058_, int p_294415_, int p_294535_, int p_295510_) {
        this.blitSprite(p_415703_, p_294915_, p_295058_, p_294415_, p_294535_, p_295510_, -1);
    }

    public void blitSprite(RenderPipeline p_421916_, ResourceLocation p_422348_, int p_422055_, int p_421599_, int p_422343_, int p_422460_, float p_422508_) {
        this.blitSprite(p_421916_, p_422348_, p_422055_, p_421599_, p_422343_, p_422460_, ARGB.color(p_422508_, -1));
    }

    private static GuiSpriteScaling getSpriteScaling(TextureAtlasSprite p_434667_) {
        return p_434667_.contents().getAdditionalMetadata(GuiMetadataSection.TYPE).orElse(GuiMetadataSection.DEFAULT).scaling();
    }

    public void blitSprite(RenderPipeline p_415593_, ResourceLocation p_365379_, int p_294695_, int p_296458_, int p_294279_, int p_295235_, int p_295034_) {
        TextureAtlasSprite textureatlassprite = this.guiSprites.getSprite(p_365379_);
        GuiSpriteScaling guispritescaling = getSpriteScaling(textureatlassprite);
        switch (guispritescaling) {
            case GuiSpriteScaling.Stretch guispritescaling$stretch:
                this.blitSprite(p_415593_, textureatlassprite, p_294695_, p_296458_, p_294279_, p_295235_, p_295034_);
                break;
            case GuiSpriteScaling.Tile guispritescaling$tile:
                this.blitTiledSprite(
                    p_415593_,
                    textureatlassprite,
                    p_294695_,
                    p_296458_,
                    p_294279_,
                    p_295235_,
                    0,
                    0,
                    guispritescaling$tile.width(),
                    guispritescaling$tile.height(),
                    guispritescaling$tile.width(),
                    guispritescaling$tile.height(),
                    p_295034_
                );
                break;
            case GuiSpriteScaling.NineSlice guispritescaling$nineslice:
                this.blitNineSlicedSprite(p_415593_, textureatlassprite, guispritescaling$nineslice, p_294695_, p_296458_, p_294279_, p_295235_, p_295034_);
                break;
            default:
        }
    }

    public void blitSprite(
        RenderPipeline p_416634_,
        ResourceLocation p_416011_,
        int p_294223_,
        int p_296245_,
        int p_296255_,
        int p_295669_,
        int p_415652_,
        int p_415759_,
        int p_416657_,
        int p_416244_
    ) {
        this.blitSprite(p_416634_, p_416011_, p_294223_, p_296245_, p_296255_, p_295669_, p_415652_, p_415759_, p_416657_, p_416244_, -1);
    }

    public void blitSprite(
        RenderPipeline p_416106_,
        ResourceLocation p_294549_,
        int p_294560_,
        int p_295075_,
        int p_294098_,
        int p_295872_,
        int p_294414_,
        int p_362199_,
        int p_363608_,
        int p_365523_,
        int p_416361_
    ) {
        TextureAtlasSprite textureatlassprite = this.guiSprites.getSprite(p_294549_);
        GuiSpriteScaling guispritescaling = getSpriteScaling(textureatlassprite);
        if (guispritescaling instanceof GuiSpriteScaling.Stretch) {
            this.blitSprite(p_416106_, textureatlassprite, p_294560_, p_295075_, p_294098_, p_295872_, p_294414_, p_362199_, p_363608_, p_365523_, p_416361_);
        } else {
            this.enableScissor(p_294414_, p_362199_, p_294414_ + p_363608_, p_362199_ + p_365523_);
            this.blitSprite(p_416106_, p_294549_, p_294414_ - p_294098_, p_362199_ - p_295872_, p_294560_, p_295075_, p_416361_);
            this.disableScissor();
        }
    }

    public void blitSprite(RenderPipeline p_416325_, TextureAtlasSprite p_416471_, int p_416622_, int p_416202_, int p_416408_, int p_416282_) {
        this.blitSprite(p_416325_, p_416471_, p_416622_, p_416202_, p_416408_, p_416282_, -1);
    }

    public void blitSprite(RenderPipeline p_416121_, TextureAtlasSprite p_364680_, int p_295194_, int p_295164_, int p_294823_, int p_295650_, int p_295401_) {
        if (p_294823_ != 0 && p_295650_ != 0) {
            this.innerBlit(
                p_416121_,
                p_364680_.atlasLocation(),
                p_295194_,
                p_295194_ + p_294823_,
                p_295164_,
                p_295164_ + p_295650_,
                p_364680_.getU0(),
                p_364680_.getU1(),
                p_364680_.getV0(),
                p_364680_.getV1(),
                p_295401_
            );
        }
    }

    private void blitSprite(
        RenderPipeline p_416146_,
        TextureAtlasSprite p_295122_,
        int p_295850_,
        int p_296348_,
        int p_295804_,
        int p_296465_,
        int p_295717_,
        int p_360779_,
        int p_363595_,
        int p_364585_,
        int p_361093_
    ) {
        if (p_363595_ != 0 && p_364585_ != 0) {
            this.innerBlit(
                p_416146_,
                p_295122_.atlasLocation(),
                p_295717_,
                p_295717_ + p_363595_,
                p_360779_,
                p_360779_ + p_364585_,
                p_295122_.getU((float)p_295804_ / p_295850_),
                p_295122_.getU((float)(p_295804_ + p_363595_) / p_295850_),
                p_295122_.getV((float)p_296465_ / p_296348_),
                p_295122_.getV((float)(p_296465_ + p_364585_) / p_296348_),
                p_361093_
            );
        }
    }

    private void blitNineSlicedSprite(
        RenderPipeline p_415939_,
        TextureAtlasSprite p_294394_,
        GuiSpriteScaling.NineSlice p_295735_,
        int p_294769_,
        int p_294546_,
        int p_294421_,
        int p_295807_,
        int p_295009_
    ) {
        GuiSpriteScaling.NineSlice.Border guispritescaling$nineslice$border = p_295735_.border();
        int i = Math.min(guispritescaling$nineslice$border.left(), p_294421_ / 2);
        int j = Math.min(guispritescaling$nineslice$border.right(), p_294421_ / 2);
        int k = Math.min(guispritescaling$nineslice$border.top(), p_295807_ / 2);
        int l = Math.min(guispritescaling$nineslice$border.bottom(), p_295807_ / 2);
        if (p_294421_ == p_295735_.width() && p_295807_ == p_295735_.height()) {
            this.blitSprite(p_415939_, p_294394_, p_295735_.width(), p_295735_.height(), 0, 0, p_294769_, p_294546_, p_294421_, p_295807_, p_295009_);
        } else if (p_295807_ == p_295735_.height()) {
            this.blitSprite(p_415939_, p_294394_, p_295735_.width(), p_295735_.height(), 0, 0, p_294769_, p_294546_, i, p_295807_, p_295009_);
            this.blitNineSliceInnerSegment(
                p_415939_,
                p_295735_,
                p_294394_,
                p_294769_ + i,
                p_294546_,
                p_294421_ - j - i,
                p_295807_,
                i,
                0,
                p_295735_.width() - j - i,
                p_295735_.height(),
                p_295735_.width(),
                p_295735_.height(),
                p_295009_
            );
            this.blitSprite(
                p_415939_,
                p_294394_,
                p_295735_.width(),
                p_295735_.height(),
                p_295735_.width() - j,
                0,
                p_294769_ + p_294421_ - j,
                p_294546_,
                j,
                p_295807_,
                p_295009_
            );
        } else if (p_294421_ == p_295735_.width()) {
            this.blitSprite(p_415939_, p_294394_, p_295735_.width(), p_295735_.height(), 0, 0, p_294769_, p_294546_, p_294421_, k, p_295009_);
            this.blitNineSliceInnerSegment(
                p_415939_,
                p_295735_,
                p_294394_,
                p_294769_,
                p_294546_ + k,
                p_294421_,
                p_295807_ - l - k,
                0,
                k,
                p_295735_.width(),
                p_295735_.height() - l - k,
                p_295735_.width(),
                p_295735_.height(),
                p_295009_
            );
            this.blitSprite(
                p_415939_,
                p_294394_,
                p_295735_.width(),
                p_295735_.height(),
                0,
                p_295735_.height() - l,
                p_294769_,
                p_294546_ + p_295807_ - l,
                p_294421_,
                l,
                p_295009_
            );
        } else {
            this.blitSprite(p_415939_, p_294394_, p_295735_.width(), p_295735_.height(), 0, 0, p_294769_, p_294546_, i, k, p_295009_);
            this.blitNineSliceInnerSegment(
                p_415939_,
                p_295735_,
                p_294394_,
                p_294769_ + i,
                p_294546_,
                p_294421_ - j - i,
                k,
                i,
                0,
                p_295735_.width() - j - i,
                k,
                p_295735_.width(),
                p_295735_.height(),
                p_295009_
            );
            this.blitSprite(
                p_415939_, p_294394_, p_295735_.width(), p_295735_.height(), p_295735_.width() - j, 0, p_294769_ + p_294421_ - j, p_294546_, j, k, p_295009_
            );
            this.blitSprite(
                p_415939_, p_294394_, p_295735_.width(), p_295735_.height(), 0, p_295735_.height() - l, p_294769_, p_294546_ + p_295807_ - l, i, l, p_295009_
            );
            this.blitNineSliceInnerSegment(
                p_415939_,
                p_295735_,
                p_294394_,
                p_294769_ + i,
                p_294546_ + p_295807_ - l,
                p_294421_ - j - i,
                l,
                i,
                p_295735_.height() - l,
                p_295735_.width() - j - i,
                l,
                p_295735_.width(),
                p_295735_.height(),
                p_295009_
            );
            this.blitSprite(
                p_415939_,
                p_294394_,
                p_295735_.width(),
                p_295735_.height(),
                p_295735_.width() - j,
                p_295735_.height() - l,
                p_294769_ + p_294421_ - j,
                p_294546_ + p_295807_ - l,
                j,
                l,
                p_295009_
            );
            this.blitNineSliceInnerSegment(
                p_415939_,
                p_295735_,
                p_294394_,
                p_294769_,
                p_294546_ + k,
                i,
                p_295807_ - l - k,
                0,
                k,
                i,
                p_295735_.height() - l - k,
                p_295735_.width(),
                p_295735_.height(),
                p_295009_
            );
            this.blitNineSliceInnerSegment(
                p_415939_,
                p_295735_,
                p_294394_,
                p_294769_ + i,
                p_294546_ + k,
                p_294421_ - j - i,
                p_295807_ - l - k,
                i,
                k,
                p_295735_.width() - j - i,
                p_295735_.height() - l - k,
                p_295735_.width(),
                p_295735_.height(),
                p_295009_
            );
            this.blitNineSliceInnerSegment(
                p_415939_,
                p_295735_,
                p_294394_,
                p_294769_ + p_294421_ - j,
                p_294546_ + k,
                j,
                p_295807_ - l - k,
                p_295735_.width() - j,
                k,
                j,
                p_295735_.height() - l - k,
                p_295735_.width(),
                p_295735_.height(),
                p_295009_
            );
        }
    }

    private void blitNineSliceInnerSegment(
        RenderPipeline p_415702_,
        GuiSpriteScaling.NineSlice p_371657_,
        TextureAtlasSprite p_371812_,
        int p_371894_,
        int p_371565_,
        int p_371606_,
        int p_371781_,
        int p_371379_,
        int p_371448_,
        int p_371442_,
        int p_371801_,
        int p_371588_,
        int p_371206_,
        int p_371311_
    ) {
        if (p_371606_ > 0 && p_371781_ > 0) {
            if (p_371657_.stretchInner()) {
                this.innerBlit(
                    p_415702_,
                    p_371812_.atlasLocation(),
                    p_371894_,
                    p_371894_ + p_371606_,
                    p_371565_,
                    p_371565_ + p_371781_,
                    p_371812_.getU((float)p_371379_ / p_371588_),
                    p_371812_.getU((float)(p_371379_ + p_371442_) / p_371588_),
                    p_371812_.getV((float)p_371448_ / p_371206_),
                    p_371812_.getV((float)(p_371448_ + p_371801_) / p_371206_),
                    p_371311_
                );
            } else {
                this.blitTiledSprite(
                    p_415702_,
                    p_371812_,
                    p_371894_,
                    p_371565_,
                    p_371606_,
                    p_371781_,
                    p_371379_,
                    p_371448_,
                    p_371442_,
                    p_371801_,
                    p_371588_,
                    p_371206_,
                    p_371311_
                );
            }
        }
    }

    private void blitTiledSprite(
        RenderPipeline p_415914_,
        TextureAtlasSprite p_294349_,
        int p_295093_,
        int p_296434_,
        int p_295268_,
        int p_295203_,
        int p_296398_,
        int p_295542_,
        int p_296165_,
        int p_296256_,
        int p_294814_,
        int p_296352_,
        int p_296203_
    ) {
        if (p_295268_ > 0 && p_295203_ > 0) {
            if (p_296165_ > 0 && p_296256_ > 0) {
                GpuTextureView gputextureview = this.minecraft.getTextureManager().getTexture(p_294349_.atlasLocation()).getTextureView();
                this.submitTiledBlit(
                    p_415914_,
                    gputextureview,
                    p_296165_,
                    p_296256_,
                    p_295093_,
                    p_296434_,
                    p_295093_ + p_295268_,
                    p_296434_ + p_295203_,
                    p_294349_.getU((float)p_296398_ / p_294814_),
                    p_294349_.getU((float)(p_296398_ + p_296165_) / p_294814_),
                    p_294349_.getV((float)p_295542_ / p_296352_),
                    p_294349_.getV((float)(p_295542_ + p_296256_) / p_296352_),
                    p_296203_
                );
            } else {
                throw new IllegalArgumentException("Tile size must be positive, got " + p_296165_ + "x" + p_296256_);
            }
        }
    }

    public void blit(
        RenderPipeline p_416258_,
        ResourceLocation p_283573_,
        int p_283574_,
        int p_283670_,
        float p_283029_,
        float p_283061_,
        int p_283545_,
        int p_282845_,
        int p_282558_,
        int p_282832_,
        int p_416564_
    ) {
        this.blit(p_416258_, p_283573_, p_283574_, p_283670_, p_283029_, p_283061_, p_283545_, p_282845_, p_283545_, p_282845_, p_282558_, p_282832_, p_416564_);
    }

    public void blit(
        RenderPipeline p_416366_,
        ResourceLocation p_282639_,
        int p_282732_,
        int p_283541_,
        float p_282660_,
        float p_281522_,
        int p_281760_,
        int p_283298_,
        int p_283429_,
        int p_282193_
    ) {
        this.blit(p_416366_, p_282639_, p_282732_, p_283541_, p_282660_, p_281522_, p_281760_, p_283298_, p_281760_, p_283298_, p_283429_, p_282193_);
    }

    public void blit(
        RenderPipeline p_416301_,
        ResourceLocation p_416627_,
        int p_416262_,
        int p_416224_,
        float p_415758_,
        float p_415576_,
        int p_416133_,
        int p_416212_,
        int p_416081_,
        int p_416306_,
        int p_416728_,
        int p_416477_
    ) {
        this.blit(p_416301_, p_416627_, p_416262_, p_416224_, p_415758_, p_415576_, p_416133_, p_416212_, p_416081_, p_416306_, p_416728_, p_416477_, -1);
    }

    public void blit(
        RenderPipeline p_416403_,
        ResourceLocation p_363701_,
        int p_282225_,
        int p_281487_,
        float p_363958_,
        float p_363869_,
        int p_281985_,
        int p_281329_,
        int p_283035_,
        int p_363829_,
        int p_365041_,
        int p_361356_,
        int p_363808_
    ) {
        this.innerBlit(
            p_416403_,
            p_363701_,
            p_282225_,
            p_282225_ + p_281985_,
            p_281487_,
            p_281487_ + p_281329_,
            (p_363958_ + 0.0F) / p_365041_,
            (p_363958_ + p_283035_) / p_365041_,
            (p_363869_ + 0.0F) / p_361356_,
            (p_363869_ + p_363829_) / p_361356_,
            p_363808_
        );
    }

    public void blit(
        ResourceLocation p_282034_,
        int p_283671_,
        int p_282377_,
        int p_282058_,
        int p_281939_,
        float p_282285_,
        float p_283199_,
        float p_415667_,
        float p_416411_
    ) {
        this.innerBlit(RenderPipelines.GUI_TEXTURED, p_282034_, p_283671_, p_282058_, p_282377_, p_281939_, p_282285_, p_283199_, p_415667_, p_416411_, -1);
    }

    private void innerBlit(
        RenderPipeline p_415722_,
        ResourceLocation p_283254_,
        int p_283092_,
        int p_281930_,
        int p_282113_,
        int p_281388_,
        float p_281327_,
        float p_281676_,
        float p_283166_,
        float p_282630_,
        int p_283583_
    ) {
        GpuTextureView gputextureview = this.minecraft.getTextureManager().getTexture(p_283254_).getTextureView();
        this.submitBlit(p_415722_, gputextureview, p_283092_, p_282113_, p_281930_, p_281388_, p_281327_, p_281676_, p_283166_, p_282630_, p_283583_);
    }

    private void submitBlit(
        RenderPipeline p_416205_,
        GpuTextureView p_423465_,
        int p_415899_,
        int p_415585_,
        int p_416253_,
        int p_416402_,
        float p_415781_,
        float p_415619_,
        float p_416198_,
        float p_415668_,
        int p_415686_
    ) {
        this.guiRenderState
            .submitGuiElement(
                new BlitRenderState(
                    p_416205_,
                    TextureSetup.singleTexture(p_423465_),
                    new Matrix3x2f(this.pose),
                    p_415899_,
                    p_415585_,
                    p_416253_,
                    p_416402_,
                    p_415781_,
                    p_415619_,
                    p_416198_,
                    p_415668_,
                    p_415686_,
                    this.scissorStack.peek()
                )
            );
    }

    private void submitTiledBlit(
        RenderPipeline p_449407_,
        GpuTextureView p_449437_,
        int p_449341_,
        int p_449290_,
        int p_449760_,
        int p_449749_,
        int p_449577_,
        int p_449669_,
        float p_449837_,
        float p_449755_,
        float p_449269_,
        float p_449219_,
        int p_449864_
    ) {
        this.guiRenderState
            .submitGuiElement(
                new TiledBlitRenderState(
                    p_449407_,
                    TextureSetup.singleTexture(p_449437_),
                    new Matrix3x2f(this.pose),
                    p_449341_,
                    p_449290_,
                    p_449760_,
                    p_449749_,
                    p_449577_,
                    p_449669_,
                    p_449837_,
                    p_449755_,
                    p_449269_,
                    p_449219_,
                    p_449864_,
                    this.scissorStack.peek()
                )
            );
    }

    /**
     * Renders an item stack at the specified coordinates.
     *
     * @param stack the item stack to render.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     */
    public void renderItem(ItemStack stack, int x, int y) {
        this.renderItem(this.minecraft.player, this.minecraft.level, stack, x, y, 0);
    }

    /**
     * Renders an item stack at the specified coordinates with a random seed.
     *
     * @param stack the item stack to render.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     * @param seed  the random seed.
     */
    public void renderItem(ItemStack stack, int x, int y, int seed) {
        this.renderItem(this.minecraft.player, this.minecraft.level, stack, x, y, seed);
    }

    /**
     * Renders a fake item stack at the specified coordinates.
     *
     * @param stack the fake item stack to render.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     */
    public void renderFakeItem(ItemStack stack, int x, int y) {
        this.renderFakeItem(stack, x, y, 0);
    }

    public void renderFakeItem(ItemStack stack, int x, int y, int seed) {
        this.renderItem(null, this.minecraft.level, stack, x, y, seed);
    }

    /**
     * Renders an item stack for a living entity at the specified coordinates with a random seed.
     *
     * @param entity the living entity.
     * @param stack  the item stack to render.
     * @param x      the x-coordinate of the rendering position.
     * @param y      the y-coordinate of the rendering position.
     * @param seed   the random seed.
     */
    public void renderItem(LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        this.renderItem(entity, entity.level(), stack, x, y, seed);
    }

    /**
     * Renders an item stack for a living entity in a specific level at the specified coordinates with a random seed.
     *
     * @param entity the living entity. Can be null.
     * @param level  the level in which the rendering occurs. Can be null.
     * @param stack  the item stack to render.
     * @param x      the x-coordinate of the rendering position.
     * @param y      the y-coordinate of the rendering position.
     * @param seed   the random seed.
     */
    private void renderItem(@Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed) {
        if (!stack.isEmpty()) {
            TrackingItemStackRenderState trackingitemstackrenderstate = new TrackingItemStackRenderState();
            this.minecraft
                .getItemModelResolver()
                .updateForTopItem(trackingitemstackrenderstate, stack, ItemDisplayContext.GUI, level, entity, seed);

            try {
                this.guiRenderState
                    .submitItem(
                        new GuiItemRenderState(
                            stack.getItem().getName().toString(),
                            new Matrix3x2f(this.pose),
                            trackingitemstackrenderstate,
                            x,
                            y,
                            this.scissorStack.peek()
                        )
                    );
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering item");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Item being rendered");
                crashreportcategory.setDetail("Item Type", () -> String.valueOf(stack.getItem()));
                crashreportcategory.setDetail("Item Components", () -> String.valueOf(stack.getComponents()));
                crashreportcategory.setDetail("Item Foil", () -> String.valueOf(stack.hasFoil()));
                throw new ReportedException(crashreport);
            }
        }
    }

    /**
     * Renders additional decorations for an item stack at the specified coordinates.
     *
     * @param font  the font used for rendering text.
     * @param stack the item stack to decorate.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     */
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        this.renderItemDecorations(font, stack, x, y, null);
    }

    /**
     * Renders additional decorations for an item stack at the specified coordinates with optional custom text.
     *
     * @param font  the font used for rendering text.
     * @param stack the item stack to decorate.
     * @param x     the x-coordinate of the rendering position.
     * @param y     the y-coordinate of the rendering position.
     * @param text  the custom text to display. Can be null.
     */
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, @Nullable String text) {
        if (!stack.isEmpty()) {
            this.pose.pushMatrix();
            this.renderItemBar(stack, x, y);
            this.renderItemCooldown(stack, x, y);
            this.renderItemCount(font, stack, x, y, text);
            this.pose.popMatrix();
            // TODO 1.21.2: This probably belongs in one of the sub-methods.
            net.neoforged.neoforge.client.ItemDecoratorHandler.of(stack).render(this, font, stack, x, y);
        }
    }

    public void setTooltipForNextFrame(Component p_419574_, int p_419861_, int p_419548_) {
        this.setTooltipForNextFrame(List.of(p_419574_.getVisualOrderText()), p_419861_, p_419548_);
    }

    public void setTooltipForNextFrame(List<FormattedCharSequence> p_419480_, int p_419761_, int p_420077_) {
        this.setTooltipForNextFrame(this.minecraft.font, p_419480_, DefaultTooltipPositioner.INSTANCE, p_419761_, p_420077_, false);
    }

    public void setTooltipForNextFrame(Font p_419878_, ItemStack p_419655_, int p_419935_, int p_419559_) {
        this.tooltipStack = p_419655_;
        this.setTooltipForNextFrame(
            p_419878_,
            Screen.getTooltipFromItem(this.minecraft, p_419655_),
            p_419655_.getTooltipImage(),
            p_419935_,
            p_419559_,
            p_419655_.get(DataComponents.TOOLTIP_STYLE)
        );
        this.tooltipStack = ItemStack.EMPTY;
    }

    public void setTooltipForNextFrame(Font font, List<Component> textComponents, Optional<TooltipComponent> tooltipComponent, ItemStack stack, int mouseX, int mouseY) {
        setTooltipForNextFrame(font, textComponents, tooltipComponent, stack, mouseX, mouseY, null);
    }

    public void setTooltipForNextFrame(Font font, List<Component> textComponents, Optional<TooltipComponent> tooltipComponent, ItemStack stack, int mouseX, int mouseY, @Nullable ResourceLocation backgroundTexture) {
        this.tooltipStack = stack;
        this.setTooltipForNextFrame(font, textComponents, tooltipComponent, mouseX, mouseY, backgroundTexture);
        this.tooltipStack = ItemStack.EMPTY;
    }

    public void setTooltipForNextFrame(Font p_419603_, List<Component> p_419948_, Optional<TooltipComponent> p_419787_, int p_419566_, int p_420005_) {
        this.setTooltipForNextFrame(p_419603_, p_419948_, p_419787_, p_419566_, p_420005_, null);
    }

    public void setTooltipForNextFrame(
        Font p_420034_, List<Component> p_419494_, Optional<TooltipComponent> p_419637_, int p_419571_, int p_419535_, @Nullable ResourceLocation p_419579_
    ) {
        List<ClientTooltipComponent> list = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponents(this.tooltipStack, p_419494_, p_419637_, p_419571_, guiWidth(), guiHeight(), p_420034_);
        this.setTooltipForNextFrameInternal(p_420034_, list, p_419571_, p_419535_, DefaultTooltipPositioner.INSTANCE, p_419579_, false);
    }

    public void setTooltipForNextFrame(Font p_420070_, Component p_419840_, int p_419594_, int p_419902_) {
        this.setTooltipForNextFrame(p_420070_, p_419840_, p_419594_, p_419902_, null);
    }

    public void setTooltipForNextFrame(Font p_420056_, Component p_419744_, int p_420073_, int p_419473_, @Nullable ResourceLocation p_419848_) {
        this.setTooltipForNextFrame(p_420056_, List.of(p_419744_.getVisualOrderText()), p_420073_, p_419473_, p_419848_);
    }

    public void setComponentTooltipForNextFrame(Font p_419927_, List<Component> p_419807_, int p_419887_, int p_420035_) {
        this.setComponentTooltipForNextFrame(p_419927_, p_419807_, p_419887_, p_420035_, (ResourceLocation) null);
    }

    public void setComponentTooltipForNextFrame(Font p_419540_, List<Component> p_419714_, int p_419554_, int p_419672_, @Nullable ResourceLocation p_419660_) {
        List<ClientTooltipComponent> components = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponents(this.tooltipStack, p_419714_, p_419554_, guiWidth(), guiHeight(), p_419540_);
        this.setTooltipForNextFrameInternal(
            p_419540_,
            components,
            p_419554_,
            p_419672_,
            DefaultTooltipPositioner.INSTANCE,
            p_419660_,
            false
        );
    }

    public void setComponentTooltipForNextFrame(Font font, List<? extends net.minecraft.network.chat.FormattedText> tooltips, int mouseX, int mouseY, ItemStack stack) {
        setComponentTooltipForNextFrame(font, tooltips, mouseX, mouseY, stack, null);
    }

    public void setComponentTooltipForNextFrame(Font font, List<? extends net.minecraft.network.chat.FormattedText> tooltips, int mouseX, int mouseY, ItemStack stack, @Nullable ResourceLocation backgroundTexture) {
        this.tooltipStack = stack;
        List<ClientTooltipComponent> components = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponents(stack, tooltips, mouseX, guiWidth(), guiHeight(), font);
        this.setTooltipForNextFrameInternal(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, backgroundTexture, false);
        this.tooltipStack = ItemStack.EMPTY;
    }

    public void setComponentTooltipFromElementsForNextFrame(Font font, List<com.mojang.datafixers.util.Either<FormattedText, TooltipComponent>> elements, int mouseX, int mouseY, ItemStack stack) {
        setComponentTooltipFromElementsForNextFrame(font, elements, mouseX, mouseY, stack, null);
    }

    public void setComponentTooltipFromElementsForNextFrame(Font font, List<com.mojang.datafixers.util.Either<FormattedText, TooltipComponent>> elements, int mouseX, int mouseY, ItemStack stack, @Nullable ResourceLocation backgroundTexture) {
        this.tooltipStack = stack;
        List<ClientTooltipComponent> components = net.neoforged.neoforge.client.ClientHooks.gatherTooltipComponentsFromElements(stack, elements, mouseX, guiWidth(), guiHeight(), font);
        this.setTooltipForNextFrameInternal(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, backgroundTexture, false);
        this.tooltipStack = ItemStack.EMPTY;
    }

    public void setTooltipForNextFrame(Font p_419718_, List<? extends FormattedCharSequence> p_419502_, int p_419583_, int p_419996_) {
        this.setTooltipForNextFrame(p_419718_, p_419502_, p_419583_, p_419996_, null);
    }

    public void setTooltipForNextFrame(
        Font p_419582_, List<? extends FormattedCharSequence> p_419728_, int p_419586_, int p_420052_, @Nullable ResourceLocation p_419654_
    ) {
        this.setTooltipForNextFrameInternal(
            p_419582_,
            p_419728_.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()),
            p_419586_,
            p_420052_,
            DefaultTooltipPositioner.INSTANCE,
            p_419654_,
            false
        );
    }

    public void setTooltipForNextFrame(
        Font p_419832_, List<FormattedCharSequence> p_419662_, ClientTooltipPositioner p_419693_, int p_420011_, int p_420014_, boolean p_419517_
    ) {
        this.setTooltipForNextFrameInternal(
            p_419832_, p_419662_.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()), p_420011_, p_420014_, p_419693_, null, p_419517_
        );
    }

    private void setTooltipForNextFrameInternal(
        Font p_419941_,
        List<ClientTooltipComponent> p_419687_,
        int p_419453_,
        int p_419611_,
        ClientTooltipPositioner p_419886_,
        @Nullable ResourceLocation p_419692_,
        boolean p_419788_
    ) {
        if (!p_419687_.isEmpty()) {
            if (this.deferredTooltip == null || p_419788_) {
                ItemStack capturedTooltipStack = this.tooltipStack;
                this.deferredTooltip = () -> this.renderTooltip(p_419941_, p_419687_, p_419453_, p_419611_, p_419886_, p_419692_, capturedTooltipStack);
            }
        }
    }

    /**
     * Renders a tooltip with multiple lines of formatted text at the specified mouse coordinates.
     *
     * @param font         the font used for rendering text.
     * @param tooltipLines the lines of the tooltip as formatted character sequences.
     * @param mouseX       the x-coordinate of the mouse position.
     * @param mouseY       the y-coordinate of the mouse position.
     */
    public void renderTooltip(
        Font font,
        List<ClientTooltipComponent> tooltipLines,
        int mouseX,
        int mouseY,
        ClientTooltipPositioner p_419610_,
        @Nullable ResourceLocation p_371766_
    ) {
        this.renderTooltip(font, tooltipLines, mouseX, mouseY, p_419610_, p_371766_, ItemStack.EMPTY);
    }

    /**
     * Renders a tooltip with multiple lines of formatted text at the specified mouse coordinates.
     *
     * @param font         the font used for rendering text.
     * @param tooltipLines the lines of the tooltip as formatted character sequences.
     * @param mouseX       the x-coordinate of the mouse position.
     * @param mouseY       the y-coordinate of the mouse position.
     */
    public void renderTooltip(
            Font font,
            List<ClientTooltipComponent> tooltipLines,
            int mouseX,
            int mouseY,
            ClientTooltipPositioner p_419610_,
            @Nullable ResourceLocation p_371766_,
            ItemStack tooltipStack
    ) {
        var preEvent = net.neoforged.neoforge.client.ClientHooks.onRenderTooltipPre(tooltipStack, this, mouseX, mouseY, guiWidth(), guiHeight(), tooltipLines, font, p_419610_);
        if (preEvent.isCanceled()) return;

        font = preEvent.getFont();
        mouseX = preEvent.getX();
        mouseY = preEvent.getY();

        int i = 0;
        int j = tooltipLines.size() == 1 ? -2 : 0;

        for (ClientTooltipComponent clienttooltipcomponent : tooltipLines) {
            int k = clienttooltipcomponent.getWidth(font);
            if (k > i) {
                i = k;
            }

            j += clienttooltipcomponent.getHeight(font);
        }

        int l1 = i;
        int i2 = j;
        Vector2ic vector2ic = p_419610_.positionTooltip(this.guiWidth(), this.guiHeight(), mouseX, mouseY, i, j);
        int l = vector2ic.x();
        int i1 = vector2ic.y();
        this.pose.pushMatrix();
        var textureEvent = net.neoforged.neoforge.client.ClientHooks.onRenderTooltipTexture(this.tooltipStack, this, l, i1, preEvent.getFont(), tooltipLines, p_371766_);
        TooltipRenderUtil.renderTooltipBackground(this, l, i1, i, j, textureEvent.getTexture());
        int j1 = i1;

        for (int k1 = 0; k1 < tooltipLines.size(); k1++) {
            ClientTooltipComponent clienttooltipcomponent1 = tooltipLines.get(k1);
            clienttooltipcomponent1.renderText(this, font, l, j1);
            j1 += clienttooltipcomponent1.getHeight(font) + (k1 == 0 ? 2 : 0);
        }

        j1 = i1;

        for (int j2 = 0; j2 < tooltipLines.size(); j2++) {
            ClientTooltipComponent clienttooltipcomponent2 = tooltipLines.get(j2);
            clienttooltipcomponent2.renderImage(font, l, j1, l1, i2, this);
            j1 += clienttooltipcomponent2.getHeight(font) + (j2 == 0 ? 2 : 0);
        }

        this.pose.popMatrix();
    }

    public void renderDeferredElements() {
        if (!this.deferredOutlines.isEmpty()) {
            this.nextStratum();

            for (GuiGraphics.OutlineBox guigraphics$outlinebox : this.deferredOutlines) {
                guigraphics$outlinebox.render(this);
            }

            this.deferredOutlines.clear();
        }

        if (this.deferredTooltip != null) {
            this.nextStratum();
            this.deferredTooltip.run();
            this.deferredTooltip = null;
        }
    }

    private void renderItemBar(ItemStack p_380278_, int p_379972_, int p_379916_) {
        if (p_380278_.isBarVisible()) {
            int i = p_379972_ + 2;
            int j = p_379916_ + 13;
            this.fill(RenderPipelines.GUI, i, j, i + 13, j + 2, -16777216);
            this.fill(RenderPipelines.GUI, i, j, i + p_380278_.getBarWidth(), j + 1, ARGB.opaque(p_380278_.getBarColor()));
        }
    }

    private void renderItemCount(Font p_380115_, ItemStack p_379291_, int p_379544_, int p_380291_, @Nullable String p_380189_) {
        if (p_379291_.getCount() != 1 || p_380189_ != null) {
            String s = p_380189_ == null ? String.valueOf(p_379291_.getCount()) : p_380189_;
            this.drawString(p_380115_, s, p_379544_ + 19 - 2 - p_380115_.width(s), p_380291_ + 6 + 3, -1, true);
        }
    }

    private void renderItemCooldown(ItemStack p_380199_, int p_380397_, int p_379741_) {
        LocalPlayer localplayer = this.minecraft.player;
        float f = localplayer == null
            ? 0.0F
            : localplayer.getCooldowns().getCooldownPercent(p_380199_, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (f > 0.0F) {
            int i = p_379741_ + Mth.floor(16.0F * (1.0F - f));
            int j = i + Mth.ceil(16.0F * f);
            this.fill(RenderPipelines.GUI, p_380397_, i, p_380397_ + 16, j, Integer.MAX_VALUE);
        }
    }

    /**
     * Renders a hover effect for a text component at the specified mouse coordinates.
     *
     * @param font   the font used for rendering text.
     * @param style  the style of the text component. Can be null.
     * @param mouseX the x-coordinate of the mouse position.
     * @param mouseY the y-coordinate of the mouse position.
     */
    public void renderComponentHoverEffect(Font font, @Nullable Style style, int mouseX, int mouseY) {
        if (style != null) {
            if (style.getClickEvent() != null) {
                this.requestCursor(CursorTypes.POINTING_HAND);
            }

            if (style.getHoverEvent() != null) {
                switch (style.getHoverEvent()) {
                    case HoverEvent.ShowItem(ItemStack itemstack):
                        this.setTooltipForNextFrame(font, itemstack, mouseX, mouseY);
                        break;
                    case HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo hoverevent$entitytooltipinfo1):
                        HoverEvent.EntityTooltipInfo hoverevent$entitytooltipinfo = hoverevent$entitytooltipinfo1;
                        if (this.minecraft.options.advancedItemTooltips) {
                            this.setComponentTooltipForNextFrame(font, hoverevent$entitytooltipinfo.getTooltipLines(), mouseX, mouseY);
                        }
                        break;
                    case HoverEvent.ShowText(Component component):
                        this.setTooltipForNextFrame(font, font.split(component, Math.max(this.guiWidth() / 2, 200)), mouseX, mouseY);
                        break;
                    default:
                }
            }
        }
    }

    public void submitMapRenderState(MapRenderState p_415810_) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureManager texturemanager = minecraft.getTextureManager();
        GpuTextureView gputextureview = texturemanager.getTexture(p_415810_.texture).getTextureView();
        this.submitBlit(RenderPipelines.GUI_TEXTURED, gputextureview, 0, 0, 128, 128, 0.0F, 1.0F, 0.0F, 1.0F, -1);

        for (MapRenderState.MapDecorationRenderState maprenderstate$mapdecorationrenderstate : p_415810_.decorations) {
            if (maprenderstate$mapdecorationrenderstate.renderOnFrame) {
                this.pose.pushMatrix();
                this.pose.translate(maprenderstate$mapdecorationrenderstate.x / 2.0F + 64.0F, maprenderstate$mapdecorationrenderstate.y / 2.0F + 64.0F);
                this.pose.rotate((float) (Math.PI / 180.0) * maprenderstate$mapdecorationrenderstate.rot * 360.0F / 16.0F);
                this.pose.scale(4.0F, 4.0F);
                this.pose.translate(-0.125F, 0.125F);
                TextureAtlasSprite textureatlassprite = maprenderstate$mapdecorationrenderstate.atlasSprite;
                if (textureatlassprite != null) {
                    GpuTextureView gputextureview1 = texturemanager.getTexture(textureatlassprite.atlasLocation()).getTextureView();
                    this.submitBlit(
                        RenderPipelines.GUI_TEXTURED,
                        gputextureview1,
                        -1,
                        -1,
                        1,
                        1,
                        textureatlassprite.getU0(),
                        textureatlassprite.getU1(),
                        textureatlassprite.getV1(),
                        textureatlassprite.getV0(),
                        -1
                    );
                }

                this.pose.popMatrix();
                if (maprenderstate$mapdecorationrenderstate.name != null) {
                    Font font = minecraft.font;
                    float f = font.width(maprenderstate$mapdecorationrenderstate.name);
                    float f1 = Mth.clamp(25.0F / f, 0.0F, 6.0F / 9.0F);
                    this.pose.pushMatrix();
                    this.pose
                        .translate(
                            maprenderstate$mapdecorationrenderstate.x / 2.0F + 64.0F - f * f1 / 2.0F,
                            maprenderstate$mapdecorationrenderstate.y / 2.0F + 64.0F + 4.0F
                        );
                    this.pose.scale(f1, f1);
                    this.guiRenderState
                        .submitText(
                            new GuiTextRenderState(
                                font,
                                maprenderstate$mapdecorationrenderstate.name.getVisualOrderText(),
                                new Matrix3x2f(this.pose),
                                0,
                                0,
                                -1,
                                Integer.MIN_VALUE,
                                false,
                                this.scissorStack.peek()
                            )
                        );
                    this.pose.popMatrix();
                }
            }
        }
    }

    public void submitEntityRenderState(
        EntityRenderState p_415907_,
        float p_415695_,
        Vector3f p_415772_,
        Quaternionf p_416089_,
        @Nullable Quaternionf p_416355_,
        int p_416675_,
        int p_416412_,
        int p_415766_,
        int p_416432_
    ) {
        this.guiRenderState
            .submitPicturesInPictureState(
                new GuiEntityRenderState(
                    p_415907_, p_415772_, p_416089_, p_416355_, p_416675_, p_416412_, p_415766_, p_416432_, p_415695_, this.scissorStack.peek()
                )
            );
    }

    public void submitSkinRenderState(
        PlayerModel p_416647_,
        ResourceLocation p_416075_,
        float p_416346_,
        float p_416524_,
        float p_416465_,
        float p_416434_,
        int p_416207_,
        int p_415726_,
        int p_415642_,
        int p_416359_
    ) {
        this.guiRenderState
            .submitPicturesInPictureState(
                new GuiSkinRenderState(
                    p_416647_, p_416075_, p_416524_, p_416465_, p_416434_, p_416207_, p_415726_, p_415642_, p_416359_, p_416346_, this.scissorStack.peek()
                )
            );
    }

    public void submitBookModelRenderState(
        BookModel p_415829_,
        ResourceLocation p_415660_,
        float p_416200_,
        float p_415771_,
        float p_416342_,
        int p_416018_,
        int p_416230_,
        int p_416557_,
        int p_416220_
    ) {
        this.guiRenderState
            .submitPicturesInPictureState(
                new GuiBookModelRenderState(
                    p_415829_, p_415660_, p_415771_, p_416342_, p_416018_, p_416230_, p_416557_, p_416220_, p_416200_, this.scissorStack.peek()
                )
            );
    }

    public void submitBannerPatternRenderState(
        BannerFlagModel p_449607_, DyeColor p_415755_, BannerPatternLayers p_415569_, int p_415681_, int p_416288_, int p_416302_, int p_415867_
    ) {
        this.guiRenderState
            .submitPicturesInPictureState(
                new GuiBannerResultRenderState(p_449607_, p_415755_, p_415569_, p_415681_, p_416288_, p_416302_, p_415867_, this.scissorStack.peek())
            );
    }

    public void submitSignRenderState(Model.Simple p_434494_, float p_416226_, WoodType p_416002_, int p_415719_, int p_416488_, int p_416444_, int p_416195_) {
        this.guiRenderState
            .submitPicturesInPictureState(
                new GuiSignRenderState(p_434494_, p_416002_, p_415719_, p_416488_, p_416444_, p_416195_, p_416226_, this.scissorStack.peek())
            );
    }

    public void submitProfilerChartRenderState(List<ResultField> p_415873_, int p_415651_, int p_416392_, int p_415782_, int p_416254_) {
        this.guiRenderState
            .submitPicturesInPictureState(new GuiProfilerChartRenderState(p_415873_, p_415651_, p_416392_, p_415782_, p_416254_, this.scissorStack.peek()));
    }

    /**
     * Neo: Submit a custom {@link net.minecraft.client.gui.render.state.GuiElementRenderState} for rendering
     */
    public void submitGuiElementRenderState(net.minecraft.client.gui.render.state.GuiElementRenderState renderState) {
        this.guiRenderState.submitGuiElement(renderState);
    }

    /**
     * Neo: Submit a custom {@link net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState} for rendering
     *
     * @see net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent
     */
    public void submitPictureInPictureRenderState(net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState renderState) {
        this.guiRenderState.submitPicturesInPictureState(renderState);
    }

    /**
     * Neo: Returns the top-most scissor rectangle, if present, for use with custom {@link net.minecraft.client.gui.render.state.GuiElementRenderState}s
     * and {@link net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState}s
     */
    @Nullable
    public ScreenRectangle peekScissorStack() {
        return this.scissorStack.peek();
    }

    public TextureAtlasSprite getSprite(Material p_433908_) {
        return this.materials.get(p_433908_);
    }

    @OnlyIn(Dist.CLIENT)
    record OutlineBox(int x, int y, int width, int height, int color) {
        public void render(GuiGraphics p_437363_) {
            p_437363_.fill(this.x, this.y, this.x + this.width, this.y + 1, this.color);
            p_437363_.fill(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, this.color);
            p_437363_.fill(this.x, this.y + 1, this.x + 1, this.y + this.height - 1, this.color);
            p_437363_.fill(this.x + this.width - 1, this.y + 1, this.x + this.width, this.y + this.height - 1, this.color);
        }
    }

    /**
     * A utility class for managing a stack of screen rectangles for scissoring.
     */
    @OnlyIn(Dist.CLIENT)
    static class ScissorStack {
        private final Deque<ScreenRectangle> stack = new ArrayDeque<>();

        /**
         * Pushes a screen rectangle onto the scissor stack.
         * <p>
         * @return The resulting intersection of the pushed rectangle with the previous top rectangle on the stack, or the pushed rectangle if the stack is empty.
         *
         * @param scissor the screen rectangle to push.
         */
        public ScreenRectangle push(ScreenRectangle scissor) {
            ScreenRectangle screenrectangle = this.stack.peekLast();
            if (screenrectangle != null) {
                ScreenRectangle screenrectangle1 = Objects.requireNonNullElse(scissor.intersection(screenrectangle), ScreenRectangle.empty());
                this.stack.addLast(screenrectangle1);
                return screenrectangle1;
            } else {
                this.stack.addLast(scissor);
                return scissor;
            }
        }

        @Nullable
        public ScreenRectangle pop() {
            if (this.stack.isEmpty()) {
                throw new IllegalStateException("Scissor stack underflow");
            } else {
                this.stack.removeLast();
                return this.stack.peekLast();
            }
        }

        @Nullable
        public ScreenRectangle peek() {
            return this.stack.peekLast();
        }

        public boolean containsPoint(int x, int y) {
            return this.stack.isEmpty() ? true : this.stack.peek().containsPoint(x, y);
        }
    }
}
