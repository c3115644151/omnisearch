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
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

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
                java.net.URLConnection conn = new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (InputStream inputStream = conn.getInputStream()) {
                    byte[] imageBytes = inputStream.readAllBytes();
                    try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                        BufferedImage bufferedImage = ImageIO.read(is);
                        if (bufferedImage == null) {
                            throw new IOException("Unsupported image format or corrupt image");
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
                                Minecraft.getInstance().getTextureManager().register(location, texture);
                                textureCache.put(url, location);
                                textureSizeCache.put(url, new int[]{bufferedImage.getWidth(), bufferedImage.getHeight()});
                                downloading.remove(url);
                                onLoaded.accept(location);
                            } catch (Throwable e) {
                                e.printStackTrace();
                                downloading.remove(url);
                            }
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                downloading.remove(url);
            }
        });

        return PENDING_TEXTURE;
    }

    public static int[] getTextureSize(String url) {
        return textureSizeCache.get(url);
    }
}