package yueyang.vostok.file;

@FunctionalInterface
public interface VKFileMigrateProgressListener {
    void onProgress(VKFileMigrateProgress progress);
}
