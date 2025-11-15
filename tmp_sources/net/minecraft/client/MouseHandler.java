package net.minecraft.client;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.Mth;
import net.minecraft.util.SmoothDouble;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFWDropCallback;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class MouseHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final long DOUBLE_CLICK_THRESHOLD_MS = 250L;
    private final Minecraft minecraft;
    private boolean isLeftPressed;
    private boolean isMiddlePressed;
    private boolean isRightPressed;
    private double xpos;
    private double ypos;
    protected long lastClickTime;
    protected int lastClickButton;
    private int fakeRightMouse;
    @Nullable
    private MouseButtonInfo activeButton = null;
    private boolean ignoreFirstMove = true;
    private int clickDepth;
    private double mousePressedTime;
    private final SmoothDouble smoothTurnX = new SmoothDouble();
    private final SmoothDouble smoothTurnY = new SmoothDouble();
    private double accumulatedDX;
    private double accumulatedDY;
    private final ScrollWheelHandler scrollWheelHandler;
    private double lastHandleMovementTime = Double.MIN_VALUE;
    private boolean mouseGrabbed;

    public MouseHandler(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.scrollWheelHandler = new ScrollWheelHandler();
    }

    private void onButton(long p_446245_, MouseButtonInfo p_445718_, int p_447323_) {
        Window window = this.minecraft.getWindow();
        if (p_446245_ == window.handle()) {
            this.minecraft.getFramerateLimitTracker().onInputReceived();
            if (this.minecraft.screen != null) {
                this.minecraft.setLastInputType(InputType.MOUSE);
            }

            boolean flag = p_447323_ == 1;
            MouseButtonInfo mousebuttoninfo = this.simulateRightClick(p_445718_, flag);
            if (flag) {
                if (this.minecraft.options.touchscreen().get() && this.clickDepth++ > 0) {
                    return;
                }

                this.activeButton = mousebuttoninfo;
                this.mousePressedTime = Blaze3D.getTime();
            } else if (this.activeButton != null) {
                if (this.minecraft.options.touchscreen().get() && --this.clickDepth > 0) {
                    return;
                }

                this.activeButton = null;
            }

            if (net.neoforged.neoforge.client.ClientHooks.onMouseButtonPre(p_445718_, p_447323_)) return;
            boolean screenHandled = false;
            if (this.minecraft.getOverlay() == null) {
                if (this.minecraft.screen == null) {
                    if (!this.mouseGrabbed && flag) {
                        this.grabMouse();
                    }
                } else {
                    double d0 = this.getScaledXPos(window);
                    double d1 = this.getScaledYPos(window);
                    Screen screen = this.minecraft.screen;
                    MouseButtonEvent mousebuttonevent = new MouseButtonEvent(d0, d1, mousebuttoninfo);
                    if (flag) {
                        screen.afterMouseAction();

                        try {
                            long i = Util.getMillis();
                            boolean flag1 = i - this.lastClickTime < 250L && this.lastClickButton == mousebuttonevent.button();
                            screenHandled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(screen, mousebuttonevent, flag1);
                            if (!screenHandled) {
                                screenHandled = screen.mouseClicked(mousebuttonevent, flag1);
                                screenHandled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(screen, mousebuttonevent, flag1, screenHandled);
                            }
                            if (screenHandled) {
                                this.lastClickTime = i;
                                this.lastClickButton = mousebuttoninfo.button();
                            }
                        } catch (Throwable throwable1) {
                            CrashReport crashreport = CrashReport.forThrowable(throwable1, "mouseClicked event handler");
                            screen.fillCrashDetails(crashreport);
                            CrashReportCategory crashreportcategory = crashreport.addCategory("Mouse");
                            this.fillMousePositionDetails(crashreportcategory, window);
                            crashreportcategory.setDetail("Button", mousebuttonevent.button());
                            throw new ReportedException(crashreport);
                        }
                    } else {
                        try {
                            screenHandled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(screen, mousebuttonevent);
                            if (!screenHandled) {
                                screenHandled = screen.mouseReleased(mousebuttonevent);
                                screenHandled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(screen, mousebuttonevent, screenHandled);
                            }
                        } catch (Throwable throwable) {
                            CrashReport crashreport1 = CrashReport.forThrowable(throwable, "mouseReleased event handler");
                            screen.fillCrashDetails(crashreport1);
                            CrashReportCategory crashreportcategory1 = crashreport1.addCategory("Mouse");
                            this.fillMousePositionDetails(crashreportcategory1, window);
                            crashreportcategory1.setDetail("Button", mousebuttonevent.button());
                            throw new ReportedException(crashreport1);
                        }
                    }
                }
            }

            // Neo: we patch out the returns in the screen handler code to fire the mouse button post event
            // therefore we add a boolean to check whether the screen handled the click and if not we let
            // vanilla's fallback handle it
            if (!screenHandled) {
            if (this.minecraft.screen == null && this.minecraft.getOverlay() == null) {
                if (mousebuttoninfo.button() == 0) {
                    this.isLeftPressed = flag;
                } else if (mousebuttoninfo.button() == 2) {
                    this.isMiddlePressed = flag;
                } else if (mousebuttoninfo.button() == 1) {
                    this.isRightPressed = flag;
                }

                InputConstants.Key inputconstants$key = InputConstants.Type.MOUSE.getOrCreate(mousebuttoninfo.button());
                KeyMapping.set(inputconstants$key, flag);
                if (flag) {
                    KeyMapping.click(inputconstants$key);
                }
            }
            }
            net.neoforged.neoforge.client.ClientHooks.onMouseButtonPost(p_445718_, p_447323_);
        }
    }

    private MouseButtonInfo simulateRightClick(MouseButtonInfo p_445369_, boolean p_446615_) {
        if (InputQuirks.SIMULATE_RIGHT_CLICK_WITH_LONG_LEFT_CLICK && p_445369_.button() == 0) {
            if (p_446615_) {
                if ((p_445369_.modifiers() & 2) == 2) {
                    this.fakeRightMouse++;
                    return new MouseButtonInfo(1, p_445369_.modifiers());
                }
            } else if (this.fakeRightMouse > 0) {
                this.fakeRightMouse--;
                return new MouseButtonInfo(1, p_445369_.modifiers());
            }
        }

        return p_445369_;
    }

    public void fillMousePositionDetails(CrashReportCategory p_412950_, Window p_412953_) {
        p_412950_.setDetail(
            "Mouse location",
            () -> String.format(
                Locale.ROOT,
                "Scaled: (%f, %f). Absolute: (%f, %f)",
                getScaledXPos(p_412953_, this.xpos),
                getScaledYPos(p_412953_, this.ypos),
                this.xpos,
                this.ypos
            )
        );
        p_412950_.setDetail(
            "Screen size",
            () -> String.format(
                Locale.ROOT,
                "Scaled: (%d, %d). Absolute: (%d, %d). Scale factor of %f",
                p_412953_.getGuiScaledWidth(),
                p_412953_.getGuiScaledHeight(),
                p_412953_.getWidth(),
                p_412953_.getHeight(),
                p_412953_.getGuiScale()
            )
        );
    }

    /**
     * Will be called when a scrolling device is used, such as a mouse wheel or scrolling area of a touchpad.
     *
     * @see GLFWScrollCallbackI
     */
    private void onScroll(long windowPointer, double xOffset, double yOffset) {
        if (windowPointer == this.minecraft.getWindow().handle()) {
            this.minecraft.getFramerateLimitTracker().onInputReceived();
            boolean flag = this.minecraft.options.discreteMouseScroll().get();
            double d0 = this.minecraft.options.mouseWheelSensitivity().get();
            double d1 = (flag ? Math.signum(xOffset) : xOffset) * d0;
            double d2 = (flag ? Math.signum(yOffset) : yOffset) * d0;
            if (this.minecraft.getOverlay() == null) {
                if (this.minecraft.screen != null) {
                    double d3 = this.getScaledXPos(this.minecraft.getWindow());
                    double d4 = this.getScaledYPos(this.minecraft.getWindow());
                    if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseScrollPre(this, this.minecraft.screen, d1, d2)) {
                        if (!this.minecraft.screen.mouseScrolled(d3, d4, d1, d2)) {
                            net.neoforged.neoforge.client.ClientHooks.onScreenMouseScrollPost(this, this.minecraft.screen, d1, d2);
                        }
                    }
                    this.minecraft.screen.afterMouseAction();
                } else if (this.minecraft.player != null) {
                    Vector2i vector2i = this.scrollWheelHandler.onMouseScroll(d1, d2);
                    if (vector2i.x == 0 && vector2i.y == 0) {
                        return;
                    }

                    int i = vector2i.y == 0 ? -vector2i.x : vector2i.y;
                    if (net.neoforged.neoforge.client.ClientHooks.onMouseScroll(this, d1, d2)) return;
                    if (this.minecraft.player.isSpectator()) {
                        if (this.minecraft.gui.getSpectatorGui().isMenuActive()) {
                            this.minecraft.gui.getSpectatorGui().onMouseScrolled(-i);
                        } else {
                            float f = Mth.clamp(this.minecraft.player.getAbilities().getFlyingSpeed() + vector2i.y * 0.005F, 0.0F, 0.2F);
                            this.minecraft.player.getAbilities().setFlyingSpeed(f);
                        }
                    } else {
                        Inventory inventory = this.minecraft.player.getInventory();
                        inventory.setSelectedSlot(ScrollWheelHandler.getNextScrollWheelSelection(i, inventory.getSelectedSlot(), Inventory.getSelectionSize()));
                    }
                }
            }
        }
    }

    private void onDrop(long windowPointer, List<Path> files, int failedFiles) {
        this.minecraft.getFramerateLimitTracker().onInputReceived();
        if (this.minecraft.screen != null) {
            this.minecraft.screen.onFilesDrop(files);
        }

        if (failedFiles > 0) {
            SystemToast.onFileDropFailure(this.minecraft, failedFiles);
        }
    }

    public void setup(Window p_445436_) {
        InputConstants.setupMouseCallbacks(
            p_445436_,
            (p_91591_, p_91592_, p_91593_) -> this.minecraft.execute(() -> this.onMove(p_91591_, p_91592_, p_91593_)),
            (p_445145_, p_445146_, p_445147_, p_445148_) -> {
                MouseButtonInfo mousebuttoninfo = new MouseButtonInfo(p_445146_, p_445148_);
                this.minecraft.execute(() -> this.onButton(p_445145_, mousebuttoninfo, p_445147_));
            },
            (p_91576_, p_91577_, p_91578_) -> this.minecraft.execute(() -> this.onScroll(p_91576_, p_91577_, p_91578_)),
            (p_349790_, p_349791_, p_349792_) -> {
                List<Path> list = new ArrayList<>(p_349791_);
                int i = 0;

                for (int j = 0; j < p_349791_; j++) {
                    String s = GLFWDropCallback.getName(p_349792_, j);

                    try {
                        list.add(Paths.get(s));
                    } catch (InvalidPathException invalidpathexception) {
                        i++;
                        LOGGER.error("Failed to parse path '{}'", s, invalidpathexception);
                    }
                }

                if (!list.isEmpty()) {
                    int k = i;
                    this.minecraft.execute(() -> this.onDrop(p_349790_, list, k));
                }
            }
        );
    }

    /**
     * Will be called when the cursor is moved.
     *
     * <p>The callback function receives the cursor position, measured in screen coordinates but relative to the top-left corner of the window client area. On platforms that provide it, the full sub-pixel cursor position is passed on.</p>
     *
     * @see GLFWCursorPosCallbackI
     */
    private void onMove(long windowPointer, double xpos, double ypos) {
        if (windowPointer == this.minecraft.getWindow().handle()) {
            if (this.ignoreFirstMove) {
                this.xpos = xpos;
                this.ypos = ypos;
                this.ignoreFirstMove = false;
            } else {
                if (this.minecraft.isWindowActive()) {
                    this.accumulatedDX = this.accumulatedDX + (xpos - this.xpos);
                    this.accumulatedDY = this.accumulatedDY + (ypos - this.ypos);
                }

                this.xpos = xpos;
                this.ypos = ypos;
            }
        }
    }

    public void handleAccumulatedMovement() {
        double d0 = Blaze3D.getTime();
        double d1 = d0 - this.lastHandleMovementTime;
        this.lastHandleMovementTime = d0;
        if (this.minecraft.isWindowActive()) {
            Screen screen = this.minecraft.screen;
            boolean flag = this.accumulatedDX != 0.0 || this.accumulatedDY != 0.0;
            if (flag) {
                this.minecraft.getFramerateLimitTracker().onInputReceived();
            }

            if (screen != null && this.minecraft.getOverlay() == null && flag) {
                Window window = this.minecraft.getWindow();
                double d2 = this.getScaledXPos(window);
                double d3 = this.getScaledYPos(window);

                try {
                    screen.mouseMoved(d2, d3);
                } catch (Throwable throwable1) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable1, "mouseMoved event handler");
                    screen.fillCrashDetails(crashreport);
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Mouse");
                    this.fillMousePositionDetails(crashreportcategory, window);
                    throw new ReportedException(crashreport);
                }

                if (this.activeButton != null && this.mousePressedTime > 0.0) {
                    double d4 = getScaledXPos(window, this.accumulatedDX);
                    double d5 = getScaledYPos(window, this.accumulatedDY);

                    try {
                        MouseButtonEvent mouseButtonEvent = new MouseButtonEvent(d2, d3, this.activeButton);
                        if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseDragPre(screen, mouseButtonEvent, d4, d5)) {
                            if (!screen.mouseDragged(mouseButtonEvent, d4, d5)) {
                                net.neoforged.neoforge.client.ClientHooks.onScreenMouseDragPost(screen, mouseButtonEvent, d4, d5);
                            }
                        }
                    } catch (Throwable throwable) {
                        CrashReport crashreport1 = CrashReport.forThrowable(throwable, "mouseDragged event handler");
                        screen.fillCrashDetails(crashreport1);
                        CrashReportCategory crashreportcategory1 = crashreport1.addCategory("Mouse");
                        this.fillMousePositionDetails(crashreportcategory1, window);
                        throw new ReportedException(crashreport1);
                    }
                }

                screen.afterMouseMove();
            }

            if (this.isMouseGrabbed() && this.minecraft.player != null) {
                this.turnPlayer(d1);
            }
        }

        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;
    }

    public static double getScaledXPos(Window p_412945_, double p_412954_) {
        return p_412954_ * p_412945_.getGuiScaledWidth() / p_412945_.getScreenWidth();
    }

    public double getScaledXPos(Window p_412944_) {
        return getScaledXPos(p_412944_, this.xpos);
    }

    public static double getScaledYPos(Window p_412952_, double p_412949_) {
        return p_412949_ * p_412952_.getGuiScaledHeight() / p_412952_.getScreenHeight();
    }

    public double getScaledYPos(Window p_412942_) {
        return getScaledYPos(p_412942_, this.ypos);
    }

    private void turnPlayer(double movementTime) {
        var event = net.neoforged.neoforge.client.ClientHooks.getTurnPlayerValues(this.minecraft.options.sensitivity().get(), this.minecraft.options.smoothCamera);
        double d2 = event.getMouseSensitivity() * 0.6F + 0.2F;
        double d3 = d2 * d2 * d2;
        double d4 = d3 * 8.0;
        double d0;
        double d1;
        if (event.getCinematicCameraEnabled()) {
            double d5 = this.smoothTurnX.getNewDeltaValue(this.accumulatedDX * d4, movementTime * d4);
            double d6 = this.smoothTurnY.getNewDeltaValue(this.accumulatedDY * d4, movementTime * d4);
            d0 = d5;
            d1 = d6;
        } else if (this.minecraft.options.getCameraType().isFirstPerson() && this.minecraft.player.isScoping()) {
            this.smoothTurnX.reset();
            this.smoothTurnY.reset();
            d0 = this.accumulatedDX * d3;
            d1 = this.accumulatedDY * d3;
        } else {
            this.smoothTurnX.reset();
            this.smoothTurnY.reset();
            d0 = this.accumulatedDX * d4;
            d1 = this.accumulatedDY * d4;
        }

        this.minecraft.getTutorial().onMouse(d0, d1);
        if (this.minecraft.player != null) {
            this.minecraft.player.turn(this.minecraft.options.invertMouseX().get() ? -d0 : d0, this.minecraft.options.invertMouseY().get() ? -d1 : d1);
        }
    }

    public boolean isLeftPressed() {
        return this.isLeftPressed;
    }

    public boolean isMiddlePressed() {
        return this.isMiddlePressed;
    }

    public boolean isRightPressed() {
        return this.isRightPressed;
    }

    public double xpos() {
        return this.xpos;
    }

    public double ypos() {
        return this.ypos;
    }

    public double getXVelocity() {
        return this.accumulatedDX;
    }

    public double getYVelocity() {
        return this.accumulatedDY;
    }

    public void setIgnoreFirstMove() {
        this.ignoreFirstMove = true;
    }

    public boolean isMouseGrabbed() {
        return this.mouseGrabbed;
    }

    public void grabMouse() {
        if (this.minecraft.isWindowActive()) {
            if (!this.mouseGrabbed) {
                if (InputQuirks.RESTORE_KEY_STATE_AFTER_MOUSE_GRAB) {
                    KeyMapping.setAll();
                }

                this.mouseGrabbed = true;
                this.xpos = this.minecraft.getWindow().getScreenWidth() / 2;
                this.ypos = this.minecraft.getWindow().getScreenHeight() / 2;
                InputConstants.grabOrReleaseMouse(this.minecraft.getWindow(), 212995, this.xpos, this.ypos);
                this.minecraft.setScreen(null);
                this.minecraft.missTime = 10000;
                this.ignoreFirstMove = true;
            }
        }
    }

    public void releaseMouse() {
        if (this.mouseGrabbed) {
            this.mouseGrabbed = false;
            this.xpos = this.minecraft.getWindow().getScreenWidth() / 2;
            this.ypos = this.minecraft.getWindow().getScreenHeight() / 2;
            InputConstants.grabOrReleaseMouse(this.minecraft.getWindow(), 212993, this.xpos, this.ypos);
        }
    }

    public void cursorEntered() {
        this.ignoreFirstMove = true;
    }

    public void drawDebugMouseInfo(Font p_412951_, GuiGraphics p_412946_) {
        Window window = this.minecraft.getWindow();
        double d0 = this.getScaledXPos(window);
        double d1 = this.getScaledYPos(window) - 8.0;
        String s = String.format(Locale.ROOT, "%.0f,%.0f", d0, d1);
        p_412946_.drawString(p_412951_, s, (int)d0, (int)d1, -1);
    }
}
