package yueyang.vostok.query;

/**
 * 查询条件支持的操作符。
 */
public enum VKOperator {
    EQ,
    NE,
    GT,
    GE,
    LT,
    LE,
    LIKE,
    IN,
    NOT_IN,
    BETWEEN,
    EXISTS,
    NOT_EXISTS,
    IS_NULL,
    IS_NOT_NULL
}
