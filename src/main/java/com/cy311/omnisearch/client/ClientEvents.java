package com.cy311.omnisearch.client;

import com.cy311.omnisearch.KeyBinds;
import com.cy311.omnisearch.client.gui.OmnisearchScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.MutableComponent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = com.cy311.omnisearch.Omnisearch.MODID)
public class ClientEvents {

    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(KeyBinds.openOmnisearch);
    }

    private static long tabHoldStartTime = 0;
    private static ItemStack lastHoveredStack;
    private static boolean hasPrintedForItem = false;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        try {
            if (KeyBinds.openOmnisearch.consumeClick()) {
                if (Minecraft.getInstance().screen == null) {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new OmnisearchScreen(null, null));
                    });
                }
            }

            if (event.getAction() == GLFW.GLFW_PRESS && (event.getKey() == GLFW.GLFW_KEY_ENTER || event.getKey() == GLFW.GLFW_KEY_KP_ENTER)) {
                Screen s = Minecraft.getInstance().screen;
                if (s instanceof OmnisearchScreen os) {
                    Minecraft.getInstance().execute(os::requestSubmit);
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed to open Omnisearch screen!");
            t.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty()) return;

        boolean isTabKeyDown;
        try {
            Object winObj = Minecraft.getInstance().getWindow();
            long handle = 0L;
            try {
                handle = (long) winObj.getClass().getMethod("handle").invoke(winObj);
            } catch (Throwable e1) {
                try {
                    handle = (long) winObj.getClass().getMethod("getWindow").invoke(winObj);
                } catch (Throwable e2) {
                    throw e2;
                }
            }
            isTabKeyDown = org.lwjgl.glfw.GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_TAB) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            isTabKeyDown = KeyBinds.openOmnisearch.isDown();
        }
        ItemStack currentStack = event.getItemStack();

        if (isTabKeyDown) {
            if (lastHoveredStack == null || lastHoveredStack.isEmpty() || !ItemStack.isSameItemSameTags(lastHoveredStack, currentStack)) {
                lastHoveredStack = currentStack;
                tabHoldStartTime = System.currentTimeMillis();
                hasPrintedForItem = false;
            }

            long holdTime = System.currentTimeMillis() - tabHoldStartTime;
            float progress = Math.min(holdTime / 1000.0f, 1.0f);

            if (progress >= 1.0f && !hasPrintedForItem) {
                System.out.println("Item name: " + currentStack.getHoverName().getString());
                if (holdTime >= 1000) {
                    if (!hasPrintedForItem) {
                        String itemName = event.getItemStack().getHoverName().getString();
                        Screen parent = Minecraft.getInstance().screen;
                        Minecraft.getInstance().setScreen(new OmnisearchScreen(parent, itemName));
                        hasPrintedForItem = true;
                    }
                } else {
                    hasPrintedForItem = false;
                }
            }

            Font font = Minecraft.getInstance().font;
            Component originalTextComponent = Component.translatable("tooltip.omnisearch.hold_tab");
            int targetWidth = font.width(originalTextComponent);
            int bracketWidth = font.width("[]");
            int innerBarPixelWidth = targetWidth - bracketWidth;
            String barChar = "=";
            int barCharWidth = font.width(barChar);
            if (barCharWidth <= 0) barCharWidth = 4;

            int totalInnerChars = innerBarPixelWidth / barCharWidth;
            if (totalInnerChars < 0) totalInnerChars = 0;
            int filledChars = (int) (totalInnerChars * progress);

            MutableComponent progressBar = Component.literal("[").withStyle(ChatFormatting.GREEN);
            progressBar.append(Component.literal(barChar.repeat(filledChars)).withStyle(ChatFormatting.GREEN));
            progressBar.append(Component.literal(barChar.repeat(totalInnerChars - filledChars)).withStyle(ChatFormatting.DARK_GRAY));
            progressBar.append(Component.literal("]").withStyle(ChatFormatting.GREEN));

            event.getToolTip().add(Component.literal(""));
            event.getToolTip().add(progressBar);

        } else {
            tabHoldStartTime = 0;
            lastHoveredStack = null;
            hasPrintedForItem = false;
            event.getToolTip().add(Component.literal(""));
            event.getToolTip().add(Component.translatable("tooltip.omnisearch.hold_tab").withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        com.cy311.omnisearch.OmnisearchLogger.info("InputEvent.MouseScrollingEvent delta={}", event.getScrollDelta());
        Screen s = Minecraft.getInstance().screen;
        if (s instanceof OmnisearchScreen os) {
            double delta = event.getScrollDelta();
            double mx = event.getMouseX();
            double my = event.getMouseY();
            if (os.mouseScrolled(mx, my, delta)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        com.cy311.omnisearch.OmnisearchLogger.info("ScreenEvent.MouseScrolled.Pre delta={}", event.getScrollDelta());
        Screen s = event.getScreen();
        if (s instanceof OmnisearchScreen os) {
            if (os.onWheel(event.getScrollDelta())) {
                event.setCanceled(true);
            }
        }
    }
}
