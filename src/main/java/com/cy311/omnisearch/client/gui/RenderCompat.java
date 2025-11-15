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

    public static void blitSprite(GuiGraphics g, Object location, int x, int y, int w, int h) {
        try {
            Class<?> rpClass = Class.forName("net.minecraft.client.renderer.RenderPipelines");
            Object pipeline = rpClass.getField("GUI_TEXTURED").get(null);
            Class<?> renderPipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline");
            g.getClass().getMethod("blitSprite", renderPipelineClass, net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class)
                    .invoke(g, pipeline, (net.minecraft.resources.ResourceLocation) location, x, y, w, h);
            return;
        } catch (Throwable ignored) {
        }
        try {
            java.util.function.Function<net.minecraft.resources.ResourceLocation, Object> fn = getGuiTexturedFactory();
            if (fn != null) {
                g.getClass().getMethod("blitSprite", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class)
                        .invoke(g, fn, (net.minecraft.resources.ResourceLocation) location, x, y, w, h);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                    .invoke(g, (net.minecraft.resources.ResourceLocation) location, x, y, 0, 0, w, h, w, h);
        } catch (Throwable ignored) {
        }
    }

    public static void blitTexture(GuiGraphics g, net.minecraft.resources.ResourceLocation location,
                                   int x, int y, int drawW, int drawH) {
        try {
            Class<?> rpClass = Class.forName("net.minecraft.client.renderer.RenderPipelines");
            Object pipeline = rpClass.getField("GUI_TEXTURED").get(null);
            Class<?> renderPipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline");
            g.getClass().getMethod("blit", renderPipelineClass, net.minecraft.resources.ResourceLocation.class,
                    int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class, int.class)
                .invoke(g, pipeline, location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f, -1);
            return;
        } catch (Throwable ignored) {}
        try {
            java.util.function.Function<net.minecraft.resources.ResourceLocation, Object> fn = getGuiTexturedFactory();
            if (fn != null) {
                try {
                    g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                            int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class, int.class)
                        .invoke(g, fn, location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f, -1);
                    return;
                } catch (Throwable ignoredInner) {
                    g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                            int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class)
                        .invoke(g, fn, location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, location, x, y, 0, 0, drawW, drawH, drawW, drawH);
        } catch (Throwable ignored) {}
    }

    public static void blitTexture(GuiGraphics g, net.minecraft.resources.ResourceLocation location,
                                   int x, int y, int u, int v, int drawW, int drawH, int texW, int texH) {
        try {
            Class<?> rpClass = Class.forName("net.minecraft.client.renderer.RenderPipelines");
            Object pipeline = rpClass.getField("GUI_TEXTURED").get(null);
            Class<?> renderPipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline");
            g.getClass().getMethod("blit", renderPipelineClass, net.minecraft.resources.ResourceLocation.class,
                    int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, pipeline, location, x, y, (float)u, (float)v, drawW, drawH, texW, texH, -1);
            return;
        } catch (Throwable ignored) {}
        try {
            java.util.function.Function<net.minecraft.resources.ResourceLocation, Object> fn = getGuiTexturedFactory();
            if (fn != null) {
                try {
                    g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                            int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class, int.class)
                        .invoke(g, fn, location, x, y, (float)u, (float)v, drawW, drawH, texW, texH, -1);
                    return;
                } catch (Throwable ignoredInner) {
                    g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                            int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class)
                        .invoke(g, fn, location, x, y, (float)u, (float)v, drawW, drawH, texW, texH);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, location, x, y, u, v, drawW, drawH, texW, texH);
        } catch (Throwable ignored) {}
    }

    public static void blitTextureFull(GuiGraphics g, net.minecraft.resources.ResourceLocation location,
                                       int x, int y, int drawW, int drawH) {
        try {
            Class<?> rpClass = Class.forName("net.minecraft.client.renderer.RenderPipelines");
            Object pipeline = rpClass.getField("GUI_TEXTURED").get(null);
            Class<?> renderPipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline");
            g.getClass().getMethod("blit", renderPipelineClass, net.minecraft.resources.ResourceLocation.class,
                    int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class, int.class)
                .invoke(g, pipeline, location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f, -1);
            return;
        } catch (Throwable ignored) {}
        try {
            java.util.function.Function<net.minecraft.resources.ResourceLocation, Object> fn = getGuiTexturedFactory();
            if (fn != null) {
                try {
                    g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                            int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class, int.class)
                        .invoke(g, fn, location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f, -1);
                    return;
                } catch (Throwable ignoredInner) {
                    g.getClass().getMethod("blit", java.util.function.Function.class, net.minecraft.resources.ResourceLocation.class,
                            int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class)
                        .invoke(g, fn, location, x, y, drawW, drawH, 0.0f, 1.0f, 0.0f, 1.0f);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        try {
            g.getClass().getMethod("blit", net.minecraft.resources.ResourceLocation.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(g, location, x, y, 0, 0, drawW, drawH, drawW, drawH);
        } catch (Throwable ignored) {}
    }
}