package yueyang.vostok.file;

/**
 * Unzip safety options to prevent zip bomb.
 */
public final class VKUnzipOptions {
    private final boolean replaceExisting;
    private final long maxEntries;
    private final long maxTotalUncompressedBytes;
    private final long maxEntryUncompressedBytes;

    private VKUnzipOptions(Builder b) {
        this.replaceExisting = b.replaceExisting;
        this.maxEntries = b.maxEntries;
        this.maxTotalUncompressedBytes = b.maxTotalUncompressedBytes;
        this.maxEntryUncompressedBytes = b.maxEntryUncompressedBytes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static VKUnzipOptions defaults(boolean replaceExisting) {
        return builder().replaceExisting(replaceExisting).build();
    }

    public boolean replaceExisting() {
        return replaceExisting;
    }

    public long maxEntries() {
        return maxEntries;
    }

    public long maxTotalUncompressedBytes() {
        return maxTotalUncompressedBytes;
    }

    public long maxEntryUncompressedBytes() {
        return maxEntryUncompressedBytes;
    }

    public static final class Builder {
        private boolean replaceExisting = true;
        private long maxEntries = -1;
        private long maxTotalUncompressedBytes = -1;
        private long maxEntryUncompressedBytes = -1;

        private Builder() {
        }

        public Builder replaceExisting(boolean replaceExisting) {
            this.replaceExisting = replaceExisting;
            return this;
        }

        public Builder maxEntries(long maxEntries) {
            this.maxEntries = maxEntries;
            return this;
        }

        public Builder maxTotalUncompressedBytes(long maxTotalUncompressedBytes) {
            this.maxTotalUncompressedBytes = maxTotalUncompressedBytes;
            return this;
        }

        public Builder maxEntryUncompressedBytes(long maxEntryUncompressedBytes) {
            this.maxEntryUncompressedBytes = maxEntryUncompressedBytes;
            return this;
        }

        public VKUnzipOptions build() {
            return new VKUnzipOptions(this);
        }
    }
}
