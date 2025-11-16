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
    private static final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

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
            try (InputStream inputStream = new URL(url).openStream()) {
                byte[] imageBytes = inputStream.readAllBytes();
                Minecraft.getInstance().execute(() -> {
                    try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                        // 使用ImageIO读取图片，这可以处理多种格式
                        BufferedImage bufferedImage = ImageIO.read(is);
                        if (bufferedImage == null) {
                            throw new IOException("Unsupported image format or corrupt image");
                        }

                        // 将BufferedImage转换为NativeImage
                        NativeImage nativeImage = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false);
                        for (int y = 0; y < bufferedImage.getHeight(); y++) {
                            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                                int rgb = bufferedImage.getRGB(x, y);
                                nativeImage.setPixel(x, y, rgb);
                            }
                        }

                        String hash = Hashing.sha1().hashString(url, StandardCharsets.UTF_8).toString();
                        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("omnisearch", "images/" + hash);

                        DynamicTexture texture = new DynamicTexture(nativeImage);
                        Minecraft.getInstance().getTextureManager().register(location, texture);

                        textureCache.put(url, location);
                        downloading.remove(url);
                        onLoaded.accept(location);
                    } catch (IOException e) {
                        e.printStackTrace();
                        downloading.remove(url);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                downloading.remove(url);
            }
        });

        return PENDING_TEXTURE;
    }
}