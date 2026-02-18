package yueyang.vostok.file;

import java.util.ArrayList;
import java.util.List;

public final class VKFileMigrateResult {
    private String fromBaseDir;
    private String toBaseDir;
    private long totalFiles;
    private long totalDirs;
    private long migratedFiles;
    private long skippedFiles;
    private long failedFiles;
    private long totalBytes;
    private long migratedBytes;
    private long durationMs;
    private final List<Failure> failures = new ArrayList<>();

    public String fromBaseDir() {
        return fromBaseDir;
    }

    public String toBaseDir() {
        return toBaseDir;
    }

    public long totalFiles() {
        return totalFiles;
    }

    public long totalDirs() {
        return totalDirs;
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

    public long durationMs() {
        return durationMs;
    }

    public List<Failure> failures() {
        return List.copyOf(failures);
    }

    public boolean success() {
        return failedFiles == 0;
    }

    void fromBaseDir(String fromBaseDir) {
        this.fromBaseDir = fromBaseDir;
    }

    void toBaseDir(String toBaseDir) {
        this.toBaseDir = toBaseDir;
    }

    void totalFiles(long totalFiles) {
        this.totalFiles = totalFiles;
    }

    void totalDirs(long totalDirs) {
        this.totalDirs = totalDirs;
    }

    void migratedFiles(long migratedFiles) {
        this.migratedFiles = migratedFiles;
    }

    void skippedFiles(long skippedFiles) {
        this.skippedFiles = skippedFiles;
    }

    void failedFiles(long failedFiles) {
        this.failedFiles = failedFiles;
    }

    void totalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    void migratedBytes(long migratedBytes) {
        this.migratedBytes = migratedBytes;
    }

    void durationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    void addFailure(String path, String message) {
        this.failures.add(new Failure(path, message));
    }

    public static final class Failure {
        private final String path;
        private final String message;

        public Failure(String path, String message) {
            this.path = path;
            this.message = message;
        }

        public String path() {
            return path;
        }

        public String message() {
            return message;
        }
    }
}
