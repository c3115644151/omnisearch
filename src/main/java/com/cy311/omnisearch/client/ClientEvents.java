package com.cy311.omnisearch.client;

import com.cy311.omnisearch.KeyBinds;
import com.cy311.omnisearch.client.gui.OmnisearchScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.MutableComponent;

public class ClientEvents {

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(KeyBinds.openOmnisearch);
    }

    public static class ForgeEvents {
        private static long tabHoldStartTime = 0;
        private static ItemStack lastHoveredStack = ItemStack.EMPTY;
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

                // Enter 键：推迟到下一帧在界面中执行，避免与输入法提交顺序冲突
                if (event.getAction() == GLFW.GLFW_PRESS && (event.getKey() == GLFW.GLFW_KEY_ENTER || event.getKey() == GLFW.GLFW_KEY_KP_ENTER)) {
                    Screen s = Minecraft.getInstance().screen;
                    if (s instanceof OmnisearchScreen os) {
                        Minecraft.getInstance().execute(() -> os.requestSubmit());
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

            boolean isTabKeyDown = false;
            try {
                com.mojang.blaze3d.platform.Window win = Minecraft.getInstance().getWindow();
                isTabKeyDown = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_TAB);
            } catch (Throwable ignored) {
                isTabKeyDown = KeyBinds.openOmnisearch.isDown();
            }
            ItemStack currentStack = event.getItemStack();

            if (isTabKeyDown) {
                if (lastHoveredStack.isEmpty() || !ItemStack.isSameItemSameComponents(lastHoveredStack, currentStack)) {
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
                        hasPrintedForItem = false; // Reset when TAB is not held long enough
                    }
                }

                Font font = Minecraft.getInstance().font;
                Component originalTextComponent = Component.translatable("tooltip.omnisearch.hold_tab");
                int targetWidth = font.width(originalTextComponent);
                int bracketWidth = font.width("[]");
                int innerBarPixelWidth = targetWidth - bracketWidth;
                String barChar = "=";
                int barCharWidth = font.width(barChar);
                if (barCharWidth <= 0) barCharWidth = 4; // Fallback width

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
                lastHoveredStack = ItemStack.EMPTY;
                hasPrintedForItem = false;
                event.getToolTip().add(Component.literal(""));
                event.getToolTip().add(Component.translatable("tooltip.omnisearch.hold_tab").withStyle(ChatFormatting.GRAY));
            }
        }
    }
}