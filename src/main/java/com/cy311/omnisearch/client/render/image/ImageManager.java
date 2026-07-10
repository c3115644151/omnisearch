package com.cy311.omnisearch.client.render.image;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Asynchronously loads, caches, and manages image textures for document rendering.
 * <p>
 * Images are downloaded via HTTP, decoded via AWT BufferedImage,
 * converted to Minecraft NativeImage, and registered as DynamicTextures.
 * Loaded textures are cached in-memory and released via {@link #close()}.
 * <p>
 * Thread-safe: downloads run on daemon threads, texture upload is scheduled
 * on the render thread via {@link Minecraft#tell}.
 */
public class ImageManager implements AutoCloseable {

    private final Map<String, ImageEntry> cache = new ConcurrentHashMap<>();
    private final Function<String, byte[]> downloader;
    private volatile boolean closed;

    /**
     * @param downloader function that downloads raw image bytes from a URL,
     *                    using session cookies and proper headers. Returns null on failure.
     */
    public ImageManager(Function<String, byte[]> downloader) {
        this.downloader = downloader;
    }

    /**
     * Requests an image by URL. Returns a future that completes with the
     * {@link ResourceLocation} of the loaded texture on the render thread.
     * <p>
     * If the image is already cached, the future completes immediately.
     * The first call for a URL triggers an async HTTP download + texture upload.
     */
    public CompletableFuture<ResourceLocation> getImage(String url) {
        if (closed || url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        ImageEntry existing = cache.get(url);
        if (existing != null && existing.location != null) {
            return CompletableFuture.completedFuture(existing.location);
        }
        if (existing != null && existing.future != null) {
            return existing.future;
        }

        // Start loading
        log("downloading: " + url);
        CompletableFuture<ResourceLocation> future = new CompletableFuture<>();
        ImageEntry entry = new ImageEntry(null, future, null);
        cache.put(url, entry);

        Thread thread = new Thread(() -> {
            try {
                byte[] imageBytes = downloadImage(url);
                if (imageBytes == null || closed) {
                    log("no bytes for: " + url + " (null=" + (imageBytes == null) + " closed=" + closed + ")");
                    future.complete(null);
                    return;
                }

                BufferedImage awtImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (awtImage == null || closed) {
                    log("ImageIO.read failed for: " + url);
                    future.complete(null);
                    return;
                }

                int width = awtImage.getWidth();
                int height = awtImage.getHeight();
                NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = awtImage.getRGB(x, y);
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                    }
                }

                Minecraft.getInstance().tell(() -> {
                    if (closed) {
                        nativeImage.close();
                        future.complete(null);
                        return;
                    }
                    DynamicTexture dynTex = new DynamicTexture(nativeImage);
                    ResourceLocation loc = Minecraft.getInstance().getTextureManager()
                        .register("omnisearch-img-" + cache.size(), dynTex);
                    log("loaded: " + url + " (" + width + "x" + height + ")");
                    cache.put(url, new ImageEntry(loc, null, new ImageDimensions(width, height)));
                    future.complete(loc);
                });
            } catch (Exception e) {
                log("FAILED: " + url + " - " + e.getMessage());
                future.complete(null);
            }
        });
        thread.setName("omnisearch-img");
        thread.setDaemon(true);
        thread.start();

        return future;
    }

    /**
     * Returns cached image dimensions, or null if not yet loaded.
     * Useful for layout calculations before the image is ready.
     */
    @Nullable
    public ImageDimensions getCachedSize(String url) {
        ImageEntry entry = cache.get(url);
        return entry != null ? entry.dimensions : null;
    }

    /**
     * Pre-loads a batch of images in parallel.
     */
    public void preload(java.util.List<String> urls) {
        for (String url : urls) {
            getImage(url);
        }
    }

    @Override
    public void close() {
        closed = true;
        for (ImageEntry entry : cache.values()) {
            if (entry.location != null) {
                Minecraft.getInstance().getTextureManager().release(entry.location);
            }
        }
        cache.clear();
    }

    // ──────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────

    @Nullable
    private byte[] downloadImage(String url) {
        try {
            byte[] result = downloader.apply(url);
            if (result == null) {
                log("downloader returned null for: " + url);
            }
            return result;
        } catch (Exception e) {
            log("download failed: " + url + " - " + e.getMessage());
            return null;
        }
    }

    private record ImageEntry(
        @Nullable ResourceLocation location,
        @Nullable CompletableFuture<ResourceLocation> future,
        @Nullable ImageDimensions dimensions
    ) {}

    private static void log(String msg) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("omnisearch-debug.log", true))) {
            pw.println(System.currentTimeMillis() + " [ImageManager] " + msg);
        } catch (Exception ignored) {}
    }
}
