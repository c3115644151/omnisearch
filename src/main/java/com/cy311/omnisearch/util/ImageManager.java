package com.cy311.omnisearch.util;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class ImageManager {

    private static final ResourceLocation PENDING_TEXTURE = ResourceLocation.fromNamespaceAndPath("omnisearch", "textures/gui/pending.png");
    private static final Map<String, ResourceLocation> textureCache = new ConcurrentHashMap<>();
    private static final Map<String, int[]> textureSizeCache = new ConcurrentHashMap<>();
    private static final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private static final Map<String, java.util.List<Consumer<ResourceLocation>>> listeners = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    static {
        try {
            javax.imageio.spi.IIORegistry reg = javax.imageio.spi.IIORegistry.getDefaultInstance();
            String[] spiClasses = new String[] {
                    "com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi",
                    "com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi",
                    "com.twelvemonkeys.imageio.plugins.png.PNGImageReaderSpi"
            };
            for (String cn : spiClasses) {
                try {
                    Class<?> c = Class.forName(cn);
                    Object spi = c.getDeclaredConstructor().newInstance();
                    reg.registerServiceProvider(spi);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static ResourceLocation getTexture(String url, Consumer<ResourceLocation> onLoaded) {
        return getTexture(url, "https://www.mcmod.cn/", onLoaded);
    }

    public static ResourceLocation getTexture(String url, String referer, Consumer<ResourceLocation> onLoaded) {
        if (url == null || url.isEmpty()) {
            return PENDING_TEXTURE;
        }

        if (textureCache.containsKey(url)) {
            ResourceLocation loc = textureCache.get(url);
            if (onLoaded != null) {
                try { onLoaded.accept(loc); } catch (Throwable ignored) {}
            }
            return loc;
        }

        if (downloading.contains(url)) {
            if (onLoaded != null) {
                listeners.computeIfAbsent(url, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(onLoaded);
            }
            return PENDING_TEXTURE;
        }

        downloading.add(url);
        if (onLoaded != null) {
            listeners.computeIfAbsent(url, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(onLoaded);
        }
        executor.submit(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
                conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
                conn.setRequestProperty("Referer", referer);
                conn.setInstanceFollowRedirects(true);
                try (InputStream inputStream = conn.getInputStream()) {
                    byte[] imageBytes = inputStream.readAllBytes();
                    BufferedImage bufferedImage = readBuffered(imageBytes);
                    if (bufferedImage == null) {
                        String[] alts = computeAlternateUrls(url);
                        for (String alt : alts) {
                            if (alt == null) continue;
                            try {
                                java.net.HttpURLConnection c2 = (java.net.HttpURLConnection) new URL(alt).openConnection();
                                c2.setConnectTimeout(5000);
                                c2.setReadTimeout(5000);
                                c2.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                                c2.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5");
                                c2.setRequestProperty("Referer", referer);
                                try (InputStream in2 = c2.getInputStream()) {
                                    byte[] b2 = in2.readAllBytes();
                                    bufferedImage = readBuffered(b2);
                                }
                            } catch (Throwable ignored) {}
                            if (bufferedImage != null) break;
                        }
                        if (bufferedImage == null) {
                            throw new IOException("Unsupported image format or corrupt image");
                        }
                    }

                    NativeImage nativeImage = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false);
                    for (int y = 0; y < bufferedImage.getHeight(); y++) {
                        for (int x = 0; x < bufferedImage.getWidth(); x++) {
                            int rgb = bufferedImage.getRGB(x, y);
                            boolean ok = false;
                            try {
                                NativeImage.class.getMethod("setPixelRGBA", int.class, int.class, int.class).invoke(nativeImage, x, y, rgb);
                                ok = true;
                            } catch (Throwable ignored) {}
                            if (!ok) {
                                try {
                                    NativeImage.class.getMethod("setPixel", int.class, int.class, int.class).invoke(nativeImage, x, y, rgb);
                                    ok = true;
                                } catch (Throwable ignored) {}
                            }
                            if (!ok) {
                                try {
                                    NativeImage.class.getMethod("setPixelARGB", int.class, int.class, int.class).invoke(nativeImage, x, y, rgb);
                                    ok = true;
                                } catch (Throwable ignored) {}
                            }
                        }
                    }

                    String hash = Hashing.sha1().hashString(url, StandardCharsets.UTF_8).toString();
                    ResourceLocation location = ResourceLocation.fromNamespaceAndPath("omnisearch", "images/" + hash);
                    final int w0 = bufferedImage.getWidth();
                    final int h0 = bufferedImage.getHeight();
                    final String urlKey = url;

                    Minecraft.getInstance().execute(() -> {
                        try {
                            DynamicTexture texture;
                            try {
                                texture = DynamicTexture.class.getConstructor(NativeImage.class).newInstance(nativeImage);
                            } catch (Throwable e1) {
                                try {
                                    java.util.function.Supplier<String> sup = () -> "omnisearch/dynamic";
                                    texture = DynamicTexture.class.getConstructor(java.util.function.Supplier.class, NativeImage.class).newInstance(sup, nativeImage);
                                } catch (Throwable e2) {
                                    throw new RuntimeException(e2);
                                }
                            }
                            try {
                                // 优先调用常规注册
                                Minecraft.getInstance().getTextureManager().register(location, texture);
                            } catch (Throwable tReg) {
                                try {
                                    // 跨版本备用：某些版本仅提供 registerForReload
                                    Minecraft.getInstance().getTextureManager().getClass()
                                            .getMethod("registerForReload", net.minecraft.resources.ResourceLocation.class, net.minecraft.client.renderer.texture.AbstractTexture.class)
                                            .invoke(Minecraft.getInstance().getTextureManager(), location, texture);
                                } catch (Throwable ignored) {}
                            }
                            try {
                                // 跨版本稳健：主动上传像素，避免某些版本未自动上传导致不显示
                                DynamicTexture.class.getMethod("upload").invoke(texture);
                            } catch (Throwable ignored) {}
                            textureCache.put(urlKey, location);
                            textureSizeCache.put(urlKey, new int[]{w0, h0});
                            downloading.remove(url);
                            java.util.List<Consumer<ResourceLocation>> list = listeners.remove(urlKey);
                            if (list != null) {
                                for (Consumer<ResourceLocation> l : list) {
                                    try { l.accept(location); } catch (Throwable ignored) {}
                                }
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                            downloading.remove(url);
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                downloading.remove(url);
            }
        });

        return PENDING_TEXTURE;
    }

    private static BufferedImage readBuffered(byte[] bytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(is);
        }
    }

    private static String[] computeAlternateUrls(String original) {
        try {
            URL u = new URL(original);
            String host = u.getHost().toLowerCase();
            String queryJoin = (u.getQuery() == null || u.getQuery().isEmpty()) ? "?" : "&";
            String alt1 = original + queryJoin + "x-oss-process=image/format,png"; // 阿里 OSS
            String alt2 = original + queryJoin + "imageMogr2/format/png"; // 七牛 CDN 常用
            String alt3 = original + queryJoin + "format=png"; // 通用兜底
            if (host.contains("mcmod")) {
                return new String[]{alt1, alt2, alt3};
            }
            return new String[]{alt2, alt3};
        } catch (Throwable ignored) {
            return new String[]{null};
        }
    }

    public static int[] getTextureSize(String url) {
        return textureSizeCache.get(url);
    }
}