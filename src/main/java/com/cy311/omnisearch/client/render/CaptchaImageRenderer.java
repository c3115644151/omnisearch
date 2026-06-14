package com.cy311.omnisearch.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Renders a CAPTCHA image from a base64 data URI using Minecraft's native texture system.
 * <p>
 * Usage:
 * <pre>{@code
 * CaptchaImageRenderer captchaImage = CaptchaImageRenderer.fromDataUri(dataUri);
 * // In render():
 * captchaImage.render(gui, x, y, width, height);
 * // When done:
 * captchaImage.close();
 * }</pre>
 */
public class CaptchaImageRenderer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("omnisearch");
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";

    private final DynamicTexture texture;
    private final ResourceLocation location;
    private final int imageWidth;
    private final int imageHeight;
    private boolean closed;

    private CaptchaImageRenderer(DynamicTexture texture, ResourceLocation location, int width, int height) {
        this.texture = texture;
        this.location = location;
        this.imageWidth = width;
        this.imageHeight = height;
        this.closed = false;
    }

    /**
     * Creates a CaptchaImageRenderer from a data URI.
     *
     * @param dataUri the data URI (e.g. "data:image/png;base64,...")
     * @return a new renderer, or null if parsing fails
     */
    public static CaptchaImageRenderer fromDataUri(String dataUri) {
        if (dataUri == null || !dataUri.startsWith(DATA_URI_PREFIX)) {
            return null;
        }
        try {
            String base64 = dataUri.substring(DATA_URI_PREFIX.length());
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            return fromBytes(imageBytes);
        } catch (Exception e) {
            LOGGER.error("Failed to decode CAPTCHA image data URI", e);
            return null;
        }
    }

    /**
     * Creates a CaptchaImageRenderer from raw PNG bytes.
     */
    static CaptchaImageRenderer fromBytes(byte[] pngBytes) {
        try {
            // First decode using AWT to get dimensions
            BufferedImage awtImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (awtImage == null) return null;

            int width = awtImage.getWidth();
            int height = awtImage.getHeight();

            // Convert AWT BufferedImage to int[] ARGB pixels
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = awtImage.getRGB(x, y);
                    // BufferedImage gives AARRGGBB, NativeImage needs AABBGGRR
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    pixels[y * width + x] = (a << 24) | (b << 16) | (g << 8) | r;
                }
            }

            // Create NativeImage and set pixels
            NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    nativeImage.setPixelRGBA(x, y, pixels[y * width + x]);
                }
            }

            // Create texture
            DynamicTexture dynTex = new DynamicTexture(nativeImage);
            ResourceLocation loc = Minecraft.getInstance().getTextureManager()
                .register("omnisearch-captcha", dynTex);

            return new CaptchaImageRenderer(dynTex, loc, width, height);
        } catch (IOException e) {
            LOGGER.error("Failed to read CAPTCHA image bytes", e);
            return null;
        }
    }

    /**
     * Renders the CAPTCHA image at the given position, scaled to fit the given dimensions.
     */
    public void render(net.minecraft.client.gui.GuiGraphics gui, int x, int y, int width, int height) {
        if (closed || texture == null) return;
        // verified: GuiGraphics.blit(ResourceLocation, int,int,int,int,int,int,int,int,int,int) from NeoForge 1.21.1
        // Use blitInQuad or blit with the texture
        gui.blit(location, x, y, 0, 0, width, height, width, height);
    }

    /**
     * Returns the original image width.
     */
    public int getImageWidth() {
        return imageWidth;
    }

    /**
     * Returns the original image height.
     */
    public int getImageHeight() {
        return imageHeight;
    }

    @Override
    public void close() {
        if (!closed && texture != null) {
            texture.close();
            closed = true;
        }
    }
}
