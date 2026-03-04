package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.ppt.VKPptImageLoadMode;
import yueyang.vostok.office.ppt.VKPptReadOptions;
import yueyang.vostok.office.ppt.VKPptWriteOptions;

/** PPT 读写限制参数快照。 */
public final class VKPptLimits {
    private final long maxDocumentBytes;
    private final int maxSlides;
    private final int maxTextChars;
    private final int maxImages;
    private final long maxSingleImageBytes;
    private final long maxTotalImageBytes;
    private final String tempSubDir;
    private final int xxeSampleBytes;
    private final VKPptImageLoadMode imageLoadMode;

    private VKPptLimits(long maxDocumentBytes,
                        int maxSlides,
                        int maxTextChars,
                        int maxImages,
                        long maxSingleImageBytes,
                        long maxTotalImageBytes,
                        String tempSubDir,
                        int xxeSampleBytes,
                        VKPptImageLoadMode imageLoadMode) {
        this.maxDocumentBytes = maxDocumentBytes;
        this.maxSlides = maxSlides;
        this.maxTextChars = maxTextChars;
        this.maxImages = maxImages;
        this.maxSingleImageBytes = maxSingleImageBytes;
        this.maxTotalImageBytes = maxTotalImageBytes;
        this.tempSubDir = tempSubDir;
        this.xxeSampleBytes = xxeSampleBytes;
        this.imageLoadMode = imageLoadMode;
    }

    public static VKPptLimits fromRead(VKOfficeConfig config, VKPptReadOptions options) {
        VKPptReadOptions opt = options == null ? VKPptReadOptions.defaults() : options;
        return new VKPptLimits(
                pickLong(opt.maxDocumentBytes(), config.getPptMaxDocumentBytes()),
                pick(opt.maxSlides(), config.getPptMaxSlides()),
                pick(opt.maxTextChars(), config.getPptMaxTextChars()),
                pick(opt.maxImages(), config.getPptMaxImages()),
                pickLong(opt.maxSingleImageBytes(), config.getPptMaxSingleImageBytes()),
                pickLong(opt.maxTotalImageBytes(), config.getPptMaxTotalImageBytes()),
                pickSubDir(opt.tempSubDir(), formatTempSubDir(config.getOfficeTempDir(), "ppt")),
                Math.max(1024, opt.xxeSampleBytes()),
                opt.imageLoadMode() == null ? VKPptImageLoadMode.BYTES : opt.imageLoadMode());
    }

    public static VKPptLimits fromWrite(VKOfficeConfig config, VKPptWriteOptions options) {
        VKPptWriteOptions opt = options == null ? VKPptWriteOptions.defaults() : options;
        return new VKPptLimits(
                pickLong(opt.maxDocumentBytes(), config.getPptMaxDocumentBytes()),
                pick(opt.maxSlides(), config.getPptMaxSlides()),
                pick(opt.maxTextChars(), config.getPptMaxTextChars()),
                pick(opt.maxImages(), config.getPptMaxImages()),
                pickLong(opt.maxSingleImageBytes(), config.getPptMaxSingleImageBytes()),
                pickLong(opt.maxTotalImageBytes(), config.getPptMaxTotalImageBytes()),
                pickSubDir(opt.tempSubDir(), formatTempSubDir(config.getOfficeTempDir(), "ppt")),
                Math.max(1024, config.getXxeSampleBytes()),
                VKPptImageLoadMode.BYTES);
    }

    private static int pick(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static long pickLong(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private static String pickSubDir(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static String formatTempSubDir(String root, String child) {
        String base = root == null ? "" : root.trim();
        if (base.isEmpty()) {
            return child;
        }
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + "/" + child;
    }

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public int maxSlides() {
        return maxSlides;
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public int maxImages() {
        return maxImages;
    }

    public long maxSingleImageBytes() {
        return maxSingleImageBytes;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public String tempSubDir() {
        return tempSubDir;
    }

    public int xxeSampleBytes() {
        return xxeSampleBytes;
    }

    public VKPptImageLoadMode imageLoadMode() {
        return imageLoadMode;
    }
}
