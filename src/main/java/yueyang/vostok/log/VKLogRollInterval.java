package yueyang.vostok.log;

/**
 * 日志文件按时间滚动的周期策略。
 * <p>
 * 时间滚动与大小滚动（{@code maxFileSizeBytes}）同时生效，任一条件触发即执行 rotate。
 */
public enum VKLogRollInterval {
    /** 不按时间滚动，仅按文件大小滚动。 */
    NONE,
    /** 按小时滚动，整点触发。 */
    HOURLY,
    /** 按天滚动（默认），每日零点触发。 */
    DAILY,
    /** 按周滚动，每周一零点触发（ISO 周定义，周一为第一天）。 */
    WEEKLY
}
