package yueyang.vostok.file;

public final class VKFileMigrateOptions {
    private VKFileMigrateMode mode = VKFileMigrateMode.COPY_ONLY;
    private VKFileConflictStrategy conflictStrategy = VKFileConflictStrategy.FAIL;
    private boolean verifyHash = false;
    private boolean includeHidden = true;
    private boolean deleteEmptyDirsAfterMove = true;
    private boolean dryRun = false;
    private int maxRetries = 0;
    private long retryIntervalMs = 200L;
    private int parallelism = 1;
    private int queueCapacity = 1024;
    private String checkpointFile;
    private VKFileMigrateProgressListener progressListener;

    public VKFileMigrateMode getMode() {
        return mode;
    }

    public VKFileMigrateOptions mode(VKFileMigrateMode mode) {
        this.mode = mode;
        return this;
    }

    public VKFileConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public VKFileMigrateOptions conflictStrategy(VKFileConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy;
        return this;
    }

    public boolean isVerifyHash() {
        return verifyHash;
    }

    public VKFileMigrateOptions verifyHash(boolean verifyHash) {
        this.verifyHash = verifyHash;
        return this;
    }

    public boolean isIncludeHidden() {
        return includeHidden;
    }

    public VKFileMigrateOptions includeHidden(boolean includeHidden) {
        this.includeHidden = includeHidden;
        return this;
    }

    public boolean isDeleteEmptyDirsAfterMove() {
        return deleteEmptyDirsAfterMove;
    }

    public VKFileMigrateOptions deleteEmptyDirsAfterMove(boolean deleteEmptyDirsAfterMove) {
        this.deleteEmptyDirsAfterMove = deleteEmptyDirsAfterMove;
        return this;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public VKFileMigrateOptions dryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKFileMigrateOptions maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public VKFileMigrateOptions retryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
        return this;
    }

    public int getParallelism() {
        return parallelism;
    }

    public VKFileMigrateOptions parallelism(int parallelism) {
        this.parallelism = parallelism;
        return this;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public VKFileMigrateOptions queueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    public String getCheckpointFile() {
        return checkpointFile;
    }

    public VKFileMigrateOptions checkpointFile(String checkpointFile) {
        this.checkpointFile = checkpointFile;
        return this;
    }

    public VKFileMigrateProgressListener getProgressListener() {
        return progressListener;
    }

    public VKFileMigrateOptions progressListener(VKFileMigrateProgressListener progressListener) {
        this.progressListener = progressListener;
        return this;
    }
}
