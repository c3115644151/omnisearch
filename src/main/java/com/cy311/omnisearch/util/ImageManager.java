package com.cy311.omnisearch.util;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Base64;

public class ImageManager {

    private static final ResourceLocation PENDING_TEXTURE = new ResourceLocation("minecraft", "textures/misc/unknown_pack.png");
    private static final Map<String, ResourceLocation> textureCache = new ConcurrentHashMap<>();
    private static final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    static {
        try {
            javax.imageio.ImageIO.scanForPlugins();
            try {
                Class<?> spiCls = Class.forName("com.luciad.imageio.webp.WebPImageReaderSpi");
                Object spi = spiCls.getDeclaredConstructor().newInstance();
                javax.imageio.spi.IIORegistry.getDefaultInstance().registerServiceProvider(spi);
            } catch (Throwable ignored) {}
            try {
                javax.imageio.ImageReader rdr = javax.imageio.ImageIO.getImageReadersByMIMEType("image/webp").hasNext() ? javax.imageio.ImageIO.getImageReadersByMIMEType("image/webp").next() : null;
                com.cy311.omnisearch.OmnisearchLogger.info("webp reader present: {}", rdr != null);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public static ResourceLocation getTexture(String url, Consumer<ResourceLocation> onLoaded) {
        if (url == null || url.isEmpty()) {
            return PENDING_TEXTURE;
        }
        if (textureCache.containsKey(url)) {
            return textureCache.get(url);
        }
        if (downloading.contains(url)) {
            return PENDING_TEXTURE;
        }
        downloading.add(url);
        executor.submit(() -> {
            try {
                String lower = url.toLowerCase();
                boolean isData = lower.startsWith("data:");
                boolean supportedExt = isData || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp");
                byte[] imageBytes;

                if (isData) {
                    int comma = url.indexOf(',');
                    if (comma > 0) {
                        String dataPart = url.substring(comma + 1);
                        imageBytes = Base64.getDecoder().decode(dataPart);
                    } else {
                        downloading.remove(url);
                        onLoaded.accept(PENDING_TEXTURE);
                        return;
                    }
                } else if (supportedExt) {
                    try (InputStream inputStream = new URL(url).openStream()) {
                        imageBytes = inputStream.readAllBytes();
                    }
                } else {
                    downloading.remove(url);
                    onLoaded.accept(PENDING_TEXTURE);
                    return;
                }
                Minecraft.getInstance().execute(() -> {
                    try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                        BufferedImage bufferedImage = ImageIO.read(is);
                        if (bufferedImage == null) {
                            com.cy311.omnisearch.OmnisearchLogger.info("ImageIO.read returned null for url: {}", url);
                            downloading.remove(url);
                            onLoaded.accept(PENDING_TEXTURE);
                            return;
                        }
                        NativeImage nativeImage = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false);
                        for (int y = 0; y < bufferedImage.getHeight(); y++) {
                            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                                int argb = bufferedImage.getRGB(x, y);
                                int a = (argb >>> 24) & 0xFF;
                                int r = (argb >>> 16) & 0xFF;
                                int g = (argb >>> 8) & 0xFF;
                                int b = argb & 0xFF;
                                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                                nativeImage.setPixelRGBA(x, y, abgr);
                            }
                        }
                        String hash = Hashing.sha1().hashString(url, StandardCharsets.UTF_8).toString();
                        ResourceLocation location = new ResourceLocation("omnisearch", "images/" + hash);
                        DynamicTexture texture = new DynamicTexture(nativeImage);
                        Minecraft.getInstance().getTextureManager().register(location, texture);
                        textureCache.put(url, location);
                        downloading.remove(url);
                        onLoaded.accept(location);
                    } catch (IOException e) {
                        com.cy311.omnisearch.OmnisearchLogger.error("Image decode failed", e);
                        downloading.remove(url);
                        onLoaded.accept(PENDING_TEXTURE);
                    }
                });
            } catch (IOException e) {
                com.cy311.omnisearch.OmnisearchLogger.error("Image download failed", e);
                downloading.remove(url);
                onLoaded.accept(PENDING_TEXTURE);
            }
        });
        return PENDING_TEXTURE;
    }

    public static ResourceLocation getGeneratedIcon(String key) {
        ResourceLocation cached = textureCache.get(key);
        if (cached != null) return cached;
        int w = 9, h = 9;
        NativeImage img = new NativeImage(w, h, false);
        int colFull = 0xFFFF4C4C; // red
        int colBorder = 0xFFB03030;
        int colEmpty = 0x00000000;
        boolean half = key.contains("half");
        boolean empty = key.contains("empty");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0;
                // simple heart shape mask
                boolean mask = (y <= 2 && (x>=2 && x<=6)) || (y==3 && (x>=1 && x<=7)) || (y==4 && (x>=1 && x<=7)) || (y==5 && (x>=2 && x<=6)) || (y==6 && (x>=3 && x<=5)) || (y==7 && (x>=4 && x<=4));
                int rgba;
                if (!mask) {
                    rgba = colEmpty;
                } else if (empty) {
                    // border only
                    boolean border = !( (y<=1 && (x>=3&&x<=5)) || (y==2 && (x>=3&&x<=5)) || (y==3 && (x>=2&&x<=6)) || (y==4 && (x>=2&&x<=6)) || (y==5 && (x>=3&&x<=5)) || (y==6 && (x>=4&&x<=4)) );
                    rgba = border ? colBorder : colEmpty;
                } else {
                    // fill (half fill if needed)
                    boolean fill = !half || x <= 4;
                    rgba = fill ? colFull : colBorder;
                }
                img.setPixelRGBA(x, y, rgba);
            }
        }
        String id = "generated/" + key;
        ResourceLocation rl = new ResourceLocation("omnisearch", id);
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        textureCache.put(key, rl);
        return rl;
    }
}