package yueyang.vostok.file;

/**
 * Conflict strategy for copy/move directory operations.
 */
public enum VKFileConflictStrategy {
    FAIL,
    SKIP,
    OVERWRITE
}
