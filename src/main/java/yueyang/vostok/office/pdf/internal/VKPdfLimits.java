package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.pdf.VKPdfImageLoadMode;
import yueyang.vostok.office.pdf.VKPdfReadOptions;
import yueyang.vostok.office.pdf.VKPdfWriteOptions;

/** PDF 读写限制参数快照。 */
public final class VKPdfLimits {
    private final long maxDocumentBytes;
    private final int maxPages;
    private final int maxTextChars;
    private final int maxImages;
    private final long maxSingleImageBytes;
    private final long maxTotalImageBytes;
    private final int maxObjects;
    private final long maxStreamBytes;
    private final String tempSubDir;
    private final VKPdfImageLoadMode imageLoadMode;

    private VKPdfLimits(long maxDocumentBytes,
                        int maxPages,
                        int maxTextChars,
                        int maxImages,
                        long maxSingleImageBytes,
                        long maxTotalImageBytes,
                        int maxObjects,
                        long maxStreamBytes,
                        String tempSubDir,
                        VKPdfImageLoadMode imageLoadMode) {
        this.maxDocumentBytes = maxDocumentBytes;
        this.maxPages = maxPages;
        this.maxTextChars = maxTextChars;
        this.maxImages = maxImages;
        this.maxSingleImageBytes = maxSingleImageBytes;
        this.maxTotalImageBytes = maxTotalImageBytes;
        this.maxObjects = maxObjects;
        this.maxStreamBytes = maxStreamBytes;
        this.tempSubDir = tempSubDir;
        this.imageLoadMode = imageLoadMode;
    }

    public static VKPdfLimits fromRead(VKOfficeConfig config, VKPdfReadOptions options) {
        VKPdfReadOptions opt = options == null ? VKPdfReadOptions.defaults() : options;
        return new VKPdfLimits(
                pickLong(opt.maxDocumentBytes(), config.getPdfMaxDocumentBytes()),
                pick(opt.maxPages(), config.getPdfMaxPages()),
                pick(opt.maxTextChars(), config.getPdfMaxTextChars()),
                pick(opt.maxImages(), config.getPdfMaxImages()),
                pickLong(opt.maxSingleImageBytes(), config.getPdfMaxSingleImageBytes()),
                pickLong(opt.maxTotalImageBytes(), config.getPdfMaxTotalImageBytes()),
                pick(opt.maxObjects(), config.getPdfMaxObjects()),
                pickLong(opt.maxStreamBytes(), config.getPdfMaxStreamBytes()),
                config.getPdfTempDir(),
                opt.imageLoadMode() == null ? VKPdfImageLoadMode.BYTES : opt.imageLoadMode());
    }

    public static VKPdfLimits fromWrite(VKOfficeConfig config, VKPdfWriteOptions options) {
        VKPdfWriteOptions opt = options == null ? VKPdfWriteOptions.defaults() : options;
        return new VKPdfLimits(
                pickLong(opt.maxDocumentBytes(), config.getPdfMaxDocumentBytes()),
                pick(opt.maxPages(), config.getPdfMaxPages()),
                pick(opt.maxTextChars(), config.getPdfMaxTextChars()),
                pick(opt.maxImages(), config.getPdfMaxImages()),
                pickLong(opt.maxSingleImageBytes(), config.getPdfMaxSingleImageBytes()),
                pickLong(opt.maxTotalImageBytes(), config.getPdfMaxTotalImageBytes()),
                pick(opt.maxObjects(), config.getPdfMaxObjects()),
                pickLong(opt.maxStreamBytes(), config.getPdfMaxStreamBytes()),
                pickSubDir(opt.tempSubDir(), config.getPdfTempDir()),
                VKPdfImageLoadMode.BYTES);
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

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public int maxPages() {
        return maxPages;
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

    public int maxObjects() {
        return maxObjects;
    }

    public long maxStreamBytes() {
        return maxStreamBytes;
    }

    public String tempSubDir() {
        return tempSubDir;
    }

    public VKPdfImageLoadMode imageLoadMode() {
        return imageLoadMode;
    }
}
