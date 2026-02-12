package yueyang.vostok.config;

/**
 * 批处理失败策略。
 */
public enum VKBatchFailStrategy {
    /**
     * 任意分片失败即抛出异常。
     */
    FAIL_FAST,
    /**
     * 失败分片跳过，继续处理后续分片。
     */
    CONTINUE
}
