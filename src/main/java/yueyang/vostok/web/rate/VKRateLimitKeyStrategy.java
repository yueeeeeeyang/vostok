package yueyang.vostok.web.rate;

public enum VKRateLimitKeyStrategy {
    IP,
    TRACE_ID,
    HEADER,
    CUSTOM
}
