package com.cy311.omnisearch.client.render.image;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.io.FileWriter;
import java.io.PrintWriter;
import com.cy311.omnisearch.data.client.RequestExecutor;

/**
 * Asynchronously loads, caches, and manages image textures for document rendering.
 * <p>
 * Images are downloaded via HTTP, decoded via AWT BufferedImage,
 * converted to Minecraft NativeImage, and registered as DynamicTextures.
 * Loaded textures are cached in-memory and released via {@link #close()}.
 * <p>
 * Thread-safe: downloads run on RequestExecutor, texture upload is scheduled
 * on the render thread via {@link Minecraft#tell}.
 */
public class ImageManager implements AutoCloseable {

    private final Map<String, ImageEntry> cache = new ConcurrentHashMap<>();
    private final Function<String, byte[]> downloader;
    private final RequestExecutor executor;
    private volatile boolean closed;

    /**
     * @param downloader function that downloads raw image bytes from a URL,
     *                    using session cookies and proper headers. Returns null on failure.
     * @param executor   thread pool for async downloads
     */
    public ImageManager(Function<String, byte[]> downloader, RequestExecutor executor) {
        this.downloader = downloader;
        this.executor = executor;
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

        executor.submit(() -> {
            byte[] imageBytes = downloadImage(url);
            if (imageBytes == null || closed) {
                log("no bytes for: " + url + " (null=" + (imageBytes == null) + " closed=" + closed + ")");
                future.complete(null);
                return null;
            }

            // Try ImageIO first (supports WebP via webp-imageio, plus PNG/JPEG/GIF/BMP natively)
            BufferedImage awtImage = null;
            try {
                awtImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            } catch (Exception e) {
                log("ImageIO.read exception: " + e.getMessage() + " for: " + url);
            }

            if (awtImage != null && !closed) {
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
                    log("loaded(via ImageIO): " + url + " (" + width + "x" + height + ")");
                    cache.put(url, new ImageEntry(loc, null, new ImageDimensions(width, height)));
                    future.complete(loc);
                });
                return null;
            }

            // Fallback: STBImage for formats ImageIO doesn't support
            NativeImage stbImage = readWithStb(imageBytes);
            if (stbImage == null || closed) {
                log("Both ImageIO and STBImage failed for: " + url);
                future.complete(null);
                return null;
            }

            int width = stbImage.getWidth();
            int height = stbImage.getHeight();
            Minecraft.getInstance().tell(() -> {
                if (closed) {
                    stbImage.close();
                    future.complete(null);
                    return;
                }
                DynamicTexture dynTex = new DynamicTexture(stbImage);
                ResourceLocation loc = Minecraft.getInstance().getTextureManager()
                    .register("omnisearch-img-" + cache.size(), dynTex);
                log("loaded(via STB): " + url + " (" + width + "x" + height + ")");
                cache.put(url, new ImageEntry(loc, null, new ImageDimensions(width, height)));
                future.complete(loc);
            });
            return null;
        });

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

    /**
     * Clears all cached image textures and size metadata, releasing GPU resources.
     * Images will be re-downloaded and re-decoded on next request.
     */
    public void clearCache() {
        for (ImageEntry entry : cache.values()) {
            if (entry.location != null) {
                Minecraft.getInstance().getTextureManager().release(entry.location);
            }
        }
        cache.clear();
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

    /**
     * Decodes image bytes using STBImage directly, bypassing NativeImage's PNG header
     * validation. This supports WebP (and other STB-supported formats) that ImageIO
     * and NativeImage.read() cannot handle.
     */
    @Nullable
    private NativeImage readWithStb(byte[] imageBytes) {
        ByteBuffer buffer = MemoryUtil.memAlloc(imageBytes.length);
        try {
            buffer.put(imageBytes);
            buffer.rewind();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(buffer, w, h, channels, 4);
                if (pixels == null) {
                    log("STBImage failed: " + STBImage.stbi_failure_reason());
                    return null;
                }
                int width = w.get(0);
                int height = h.get(0);
                NativeImage ni = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int idx = (y * width + x) * 4;
                        int r = pixels.get(idx) & 0xFF;
                        int g = pixels.get(idx + 1) & 0xFF;
                        int b = pixels.get(idx + 2) & 0xFF;
                        int a = pixels.get(idx + 3) & 0xFF;
                        // NativeImage uses ABGR layout in memory (RGBA pixel format)
                        ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                    }
                }
                STBImage.stbi_image_free(pixels);
                return ni;
            }
        } catch (Exception e) {
            log("readWithStb exception: " + e.getMessage());
            return null;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

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
