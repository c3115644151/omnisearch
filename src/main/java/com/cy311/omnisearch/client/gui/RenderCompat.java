package com.cy311.omnisearch.client.gui;

import net.minecraft.client.gui.GuiGraphics;

import java.lang.reflect.Method;

public final class RenderCompat {
    private static boolean invoke(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            Method m = target.getClass().getMethod(name, types);
            m.setAccessible(true);
            m.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static java.util.function.Function<net.minecraft.resources.ResourceLocation, Object> getGuiTexturedFactory() {
        try {
            Class<?> rt = Class.forName("net.minecraft.client.renderer.RenderType");
            Method m;
            try {
                m = rt.getMethod("guiTextured", net.minecraft.resources.ResourceLocation.class);
            } catch (Throwable ignored) {
                try {
                    m = rt.getMethod("textured", net.minecraft.resources.ResourceLocation.class);
                } catch (Throwable ignored2) {
                    m = rt.getMethod("text", net.minecraft.resources.ResourceLocation.class);
                }
            }
            final Method fm = m;
            return loc -> {
                try {
                    return fm.invoke(null, loc);
                } catch (Throwable ignored) {
                    return null;
                }
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeNoArg(Object target, String name) {
        return invoke(target, name, new Class<?>[]{}, new Object[]{});
    }

    public static void push(GuiGraphics g) {
        Object pose = g.pose();
        if (!invokeNoArg(pose, "pushPose")) {
            invokeNoArg(pose, "pushMatrix");
        }
    }

    public static void pop(GuiGraphics g) {
        Object pose = g.pose();
        if (!invokeNoArg(pose, "popPose")) {
            invokeNoArg(pose, "popMatrix");
        }
    }

    public static void translate(GuiGraphics g, float x, float y) {
        Object pose = g.pose();
        if (invoke(pose, "translate", new Class<?>[]{float.class, float.class}, new Object[]{x, y})) {
            return;
        }
        if (invoke(pose, "translate", new Class<?>[]{int.class, int.class}, new Object[]{(int) x, (int) y})) {
            return;
        }
        invoke(pose, "translate", new Class<?>[]{float.class, float.class, float.class}, new Object[]{x, y, 0f});
    }

    public static void scale(GuiGraphics g, float sx, float sy) {
        Object pose = g.pose();
        if (invoke(pose, "scale", new Class<?>[]{float.class, float.class}, new Object[]{sx, sy})) {
            return;
        }
        invoke(pose, "scale", new Class<?>[]{float.class, float.class, float.class}, new Object[]{sx, sy, 1.0f});
    }

    public static boolean blitSpriteChecked(GuiGraphics g, net.minecraft.resources.ResourceLocation location, int x, int y, int w, int h) {
        try {
            g.getClass().getMethod("blitSprite", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class)
                    .invoke(g, location, x, y, w, h);
            com.cy311.omnisearch.OmnisearchLogger.info("blitSprite direct path used: " + location);
            return true;
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blitSprite", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class)
                    .invoke(g, getGuiTexturedFactory(), location, x, y, w, h);
            com.cy311.omnisearch.OmnisearchLogger.info("blitSprite factory path used: " + location);
            return true;
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                    .invoke(g, location, x, y, 0, 0, w, h, w, h);
            com.cy311.omnisearch.OmnisearchLogger.info("blit fallback used: " + location);
            return true;
        } catch (Throwable ignored) {}
        com.cy311.omnisearch.OmnisearchLogger.info("blitSprite failed for: " + location);
        return false;
    }

    public static boolean blitHudSprite(GuiGraphics g, net.minecraft.resources.ResourceLocation location, int x, int y, int w, int h) {
        try {
            Class<?> mcCls = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcCls.getMethod("getInstance").invoke(null);
            Object sprites = mcCls.getMethod("getGuiSprites").invoke(mc);
            Class<?> spritesCls = Class.forName("net.minecraft.client.gui.sprites.GuiSpriteManager");
            Object sprite = spritesCls.getMethod("getSprite", net.minecraft.resources.ResourceLocation.class).invoke(sprites, location);
            Class<?> spriteCls;
            try {
                spriteCls = Class.forName("net.minecraft.client.gui.sprites.GuiSprite");
            } catch (Throwable ignored) {
                spriteCls = Class.forName("net.minecraft.client.gui.sprites.GuiSpriteManager$Sprite");
            }
            g.getClass().getMethod("blitSprite", spriteCls, int.class, int.class, int.class, int.class).invoke(g, sprite, x, y, w, h);
            com.cy311.omnisearch.OmnisearchLogger.info("blitHudSprite used: " + location);
            return true;
        } catch (Throwable ignored) {}
        return blitSpriteChecked(g, location, x, y, w, h);
    }

    public static void blitTexture(GuiGraphics g, net.minecraft.resources.ResourceLocation location,
                                   int x, int y, int drawW, int drawH) {
        try {
            g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                    int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class, int.class)
                .invoke(g, getGuiTexturedFactory(), location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f, -1);
            return;
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, location, x, y, 0, 0, drawW, drawH, drawW, drawH);
        } catch (Throwable ignored) {}
    }

    public static void blitTexture(GuiGraphics g, net.minecraft.resources.ResourceLocation location,
                                   int x, int y, int u, int v, int drawW, int drawH, int texW, int texH) {
        try {
            g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                    int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, getGuiTexturedFactory(), location, x, y, (float)u, (float)v, drawW, drawH, texW, texH, -1);
            return;
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, location, x, y, u, v, drawW, drawH, texW, texH);
        } catch (Throwable ignored) {}
    }

    public static void blitTextureUVLogged(GuiGraphics g, net.minecraft.resources.ResourceLocation location,
                                           int x, int y, int u, int v, int drawW, int drawH, int texW, int texH,
                                           String tag) {
        com.cy311.omnisearch.OmnisearchLogger.info("icons blit tag=" + tag + " uv=(" + u + "," + v + ") size=(" + drawW + "," + drawH + ") tex=(" + texW + "," + texH + ")");
        blitTexture(g, location, x, y, u, v, drawW, drawH, texW, texH);
    }

    public static void blitTextureFull(GuiGraphics g, net.minecraft.resources.ResourceLocation location,
                                       int x, int y, int drawW, int drawH) {
        try {
            g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                    int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class, int.class)
                .invoke(g, getGuiTexturedFactory(), location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f, -1);
            return;
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, location, x, y, 0, 0, drawW, drawH, drawW, drawH);
        } catch (Throwable ignored) {}
    }
}