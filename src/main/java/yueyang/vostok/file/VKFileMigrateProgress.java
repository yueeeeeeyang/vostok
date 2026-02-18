package yueyang.vostok.file;

public final class VKFileMigrateProgress {
    private final VKFileMigrateProgressStatus status;
    private final String path;
    private final long totalFiles;
    private final long migratedFiles;
    private final long skippedFiles;
    private final long failedFiles;
    private final long totalBytes;
    private final long migratedBytes;
    private final int attempt;
    private final int maxRetries;
    private final String message;

    public VKFileMigrateProgress(VKFileMigrateProgressStatus status,
                                 String path,
                                 long totalFiles,
                                 long migratedFiles,
                                 long skippedFiles,
                                 long failedFiles,
                                 long totalBytes,
                                 long migratedBytes,
                                 int attempt,
                                 int maxRetries,
                                 String message) {
        this.status = status;
        this.path = path;
        this.totalFiles = totalFiles;
        this.migratedFiles = migratedFiles;
        this.skippedFiles = skippedFiles;
        this.failedFiles = failedFiles;
        this.totalBytes = totalBytes;
        this.migratedBytes = migratedBytes;
        this.attempt = attempt;
        this.maxRetries = maxRetries;
        this.message = message;
    }

    public VKFileMigrateProgressStatus status() {
        return status;
    }

    public String path() {
        return path;
    }

    public long totalFiles() {
        return totalFiles;
    }

    public long migratedFiles() {
        return migratedFiles;
    }

    public long skippedFiles() {
        return skippedFiles;
    }

    public long failedFiles() {
        return failedFiles;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public long migratedBytes() {
        return migratedBytes;
    }

    public int attempt() {
        return attempt;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public String message() {
        return message;
    }
}
