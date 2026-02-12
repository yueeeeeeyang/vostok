package yueyang.vostok.config;

/**
 * 事务传播行为。
 */
public enum VKTxPropagation {
    REQUIRED,
    REQUIRES_NEW,
    SUPPORTS,
    NOT_SUPPORTED,
    MANDATORY,
    NEVER
}
