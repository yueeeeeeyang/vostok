package yueyang.vostok.office.word.internal;

import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.word.VKWordImageLoadMode;
import yueyang.vostok.office.word.VKWordReadOptions;
import yueyang.vostok.office.word.VKWordWriteOptions;

/** Word 读写限制参数快照。 */
public final class VKWordLimits {
    private final long maxDocumentBytes;
    private final int maxTextChars;
    private final int maxImages;
    private final long maxSingleImageBytes;
    private final long maxTotalImageBytes;
    private final String tempSubDir;
    private final int xxeSampleBytes;

    private final boolean includeHeaderFooter;
    private final boolean includeFootnotes;
    private final boolean includeEndnotes;
    private final boolean includeComments;
    private final VKWordImageLoadMode imageLoadMode;

    private VKWordLimits(long maxDocumentBytes,
                         int maxTextChars,
                         int maxImages,
                         long maxSingleImageBytes,
                         long maxTotalImageBytes,
                         String tempSubDir,
                         int xxeSampleBytes,
                         boolean includeHeaderFooter,
                         boolean includeFootnotes,
                         boolean includeEndnotes,
                         boolean includeComments,
                         VKWordImageLoadMode imageLoadMode) {
        this.maxDocumentBytes = maxDocumentBytes;
        this.maxTextChars = maxTextChars;
        this.maxImages = maxImages;
        this.maxSingleImageBytes = maxSingleImageBytes;
        this.maxTotalImageBytes = maxTotalImageBytes;
        this.tempSubDir = tempSubDir;
        this.xxeSampleBytes = xxeSampleBytes;
        this.includeHeaderFooter = includeHeaderFooter;
        this.includeFootnotes = includeFootnotes;
        this.includeEndnotes = includeEndnotes;
        this.includeComments = includeComments;
        this.imageLoadMode = imageLoadMode;
    }

    public static VKWordLimits fromRead(VKOfficeConfig config, VKWordReadOptions options) {
        VKWordReadOptions opt = options == null ? VKWordReadOptions.defaults() : options;
        return new VKWordLimits(
                pickLong(opt.maxDocumentBytes(), config.getWordMaxDocumentBytes()),
                pick(opt.maxTextChars(), config.getWordMaxTextChars()),
                pick(opt.maxImages(), config.getWordMaxImages()),
                pickLong(opt.maxSingleImageBytes(), config.getWordMaxSingleImageBytes()),
                pickLong(opt.maxTotalImageBytes(), config.getWordMaxTotalImageBytes()),
                pickSubDir(opt.tempSubDir(), config.getWordTempDir()),
                Math.max(1024, opt.xxeSampleBytes()),
                opt.includeHeaderFooter(),
                opt.includeFootnotes(),
                opt.includeEndnotes(),
                opt.includeComments(),
                opt.imageLoadMode() == null ? VKWordImageLoadMode.BYTES : opt.imageLoadMode()
        );
    }

    public static VKWordLimits fromWrite(VKOfficeConfig config, VKWordWriteOptions options) {
        VKWordWriteOptions opt = options == null ? VKWordWriteOptions.defaults() : options;
        return new VKWordLimits(
                pickLong(opt.maxDocumentBytes(), config.getWordMaxDocumentBytes()),
                pick(opt.maxTextChars(), config.getWordMaxTextChars()),
                pick(opt.maxImages(), config.getWordMaxImages()),
                pickLong(opt.maxSingleImageBytes(), config.getWordMaxSingleImageBytes()),
                pickLong(opt.maxTotalImageBytes(), config.getWordMaxTotalImageBytes()),
                pickSubDir(opt.tempSubDir(), config.getWordTempDir()),
                Math.max(1024, config.getXxeSampleBytes()),
                false,
                false,
                false,
                false,
                VKWordImageLoadMode.BYTES
        );
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

    public boolean includeHeaderFooter() {
        return includeHeaderFooter;
    }

    public boolean includeFootnotes() {
        return includeFootnotes;
    }

    public boolean includeEndnotes() {
        return includeEndnotes;
    }

    public boolean includeComments() {
        return includeComments;
    }

    public VKWordImageLoadMode imageLoadMode() {
        return imageLoadMode;
    }
}
