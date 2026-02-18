package yueyang.vostok.file;

import java.awt.Color;

public final class VKThumbnailOptions {
    private final int width;
    private final int height;
    private final VKThumbnailMode mode;
    private final String format;
    private final float quality;
    private final boolean keepAspectRatio;
    private final boolean upscale;
    private final Color background;
    private final boolean stripMetadata;
    private final boolean sharpen;
    private final long maxInputPixels;
    private final long maxOutputPixels;

    private VKThumbnailOptions(Builder b) {
        this.width = b.width;
        this.height = b.height;
        this.mode = b.mode;
        this.format = b.format;
        this.quality = b.quality;
        this.keepAspectRatio = b.keepAspectRatio;
        this.upscale = b.upscale;
        this.background = b.background;
        this.stripMetadata = b.stripMetadata;
        this.sharpen = b.sharpen;
        this.maxInputPixels = b.maxInputPixels;
        this.maxOutputPixels = b.maxOutputPixels;
    }

    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public VKThumbnailMode mode() {
        return mode;
    }

    public String format() {
        return format;
    }

    public float quality() {
        return quality;
    }

    public boolean keepAspectRatio() {
        return keepAspectRatio;
    }

    public boolean upscale() {
        return upscale;
    }

    public Color background() {
        return background;
    }

    public boolean stripMetadata() {
        return stripMetadata;
    }

    public boolean sharpen() {
        return sharpen;
    }

    public long maxInputPixels() {
        return maxInputPixels;
    }

    public long maxOutputPixels() {
        return maxOutputPixels;
    }

    public static final class Builder {
        private final int width;
        private final int height;
        private VKThumbnailMode mode = VKThumbnailMode.FIT;
        private String format;
        private float quality = 0.85f;
        private boolean keepAspectRatio = true;
        private boolean upscale = false;
        private Color background = Color.WHITE;
        private boolean stripMetadata = true;
        private boolean sharpen = false;
        private long maxInputPixels = 100_000_000L;
        private long maxOutputPixels = 64_000_000L;

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public Builder mode(VKThumbnailMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder quality(float quality) {
            this.quality = quality;
            return this;
        }

        public Builder keepAspectRatio(boolean keepAspectRatio) {
            this.keepAspectRatio = keepAspectRatio;
            return this;
        }

        public Builder upscale(boolean upscale) {
            this.upscale = upscale;
            return this;
        }

        public Builder background(Color background) {
            this.background = background;
            return this;
        }

        public Builder stripMetadata(boolean stripMetadata) {
            this.stripMetadata = stripMetadata;
            return this;
        }

        public Builder sharpen(boolean sharpen) {
            this.sharpen = sharpen;
            return this;
        }

        public Builder maxInputPixels(long maxInputPixels) {
            this.maxInputPixels = maxInputPixels;
            return this;
        }

        public Builder maxOutputPixels(long maxOutputPixels) {
            this.maxOutputPixels = maxOutputPixels;
            return this;
        }

        public VKThumbnailOptions build() {
            return new VKThumbnailOptions(this);
        }
    }
}
