package yueyang.vostok.file;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * File module configuration.
 */
public final class VKFileConfig {
    private String mode = LocalTextFileStore.MODE;
    private String baseDir = System.getProperty("user.dir", ".");
    private Charset charset = StandardCharsets.UTF_8;
    private long unzipMaxEntries = -1;
    private long unzipMaxTotalUncompressedBytes = -1;
    private long unzipMaxEntryUncompressedBytes = -1;
    private boolean watchRecursiveDefault = false;

    public String getMode() {
        return mode;
    }

    public VKFileConfig mode(String mode) {
        this.mode = mode;
        return this;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public VKFileConfig baseDir(String baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public VKFileConfig charset(Charset charset) {
        this.charset = charset;
        return this;
    }

    public long getUnzipMaxEntries() {
        return unzipMaxEntries;
    }

    public VKFileConfig unzipMaxEntries(long unzipMaxEntries) {
        this.unzipMaxEntries = unzipMaxEntries;
        return this;
    }

    public long getUnzipMaxTotalUncompressedBytes() {
        return unzipMaxTotalUncompressedBytes;
    }

    public VKFileConfig unzipMaxTotalUncompressedBytes(long unzipMaxTotalUncompressedBytes) {
        this.unzipMaxTotalUncompressedBytes = unzipMaxTotalUncompressedBytes;
        return this;
    }

    public long getUnzipMaxEntryUncompressedBytes() {
        return unzipMaxEntryUncompressedBytes;
    }

    public VKFileConfig unzipMaxEntryUncompressedBytes(long unzipMaxEntryUncompressedBytes) {
        this.unzipMaxEntryUncompressedBytes = unzipMaxEntryUncompressedBytes;
        return this;
    }

    public boolean isWatchRecursiveDefault() {
        return watchRecursiveDefault;
    }

    public VKFileConfig watchRecursiveDefault(boolean watchRecursiveDefault) {
        this.watchRecursiveDefault = watchRecursiveDefault;
        return this;
    }
}
