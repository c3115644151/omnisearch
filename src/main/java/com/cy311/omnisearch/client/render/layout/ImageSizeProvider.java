package com.cy311.omnisearch.client.render.layout;

import com.cy311.omnisearch.client.render.image.ImageDimensions;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies known pixel dimensions for image URLs during layout.
 * <p>
 * Pure Java — the concrete implementation (wired to {@code ImageManager}) lives on the
 * client side. This lets {@link LayoutEngine} reserve boxes from the ACTUAL decoded size
 * when the image is already loaded, falling back to HTML width/height attributes or a
 * default box otherwise. Keeping it interface-only also keeps LayoutEngine unit-testable
 * without an ImageManager.
 */
public interface ImageSizeProvider {

    /**
     * Returns the decoded pixel size of the image at {@code url}, or null if the image is
     * not yet loaded / its size is unknown.
     */
    @Nullable
    ImageDimensions getImageSize(String url);
}
