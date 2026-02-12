package yueyang.vostok.data.dialect;

/**
 * 数据库方言接口，用于分页与关键字差异。
 */
public interface VKDialect {
    /**
     * 将分页语句追加到 SQL。
     */
    void appendLimitOffset(StringBuilder sb, Integer limit, Integer offset);
}
