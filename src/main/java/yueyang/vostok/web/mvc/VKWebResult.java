package yueyang.vostok.web.mvc;

/**
 * Web API 统一返回包装。
 *
 * @param <T> 数据泛型
 */
public final class VKWebResult<T> {
    private int statusCode;
    private String errorMessage;
    private T data;
    private long requestCostMs;
    private String traceId;
    private long requestTime;
    private long responseTime;

    public static <T> VKWebResult<T> ok(T data) {
        return of(200, null, data);
    }

    public static <T> VKWebResult<T> error(int statusCode, String errorMessage) {
        return of(statusCode, errorMessage, null);
    }

    public static <T> VKWebResult<T> of(int statusCode, String errorMessage, T data) {
        VKWebResult<T> result = new VKWebResult<>();
        result.statusCode = statusCode;
        result.errorMessage = errorMessage;
        result.data = data;
        return result;
    }

    public int statusCode() {
        return statusCode;
    }

    public VKWebResult<T> statusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public VKWebResult<T> errorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public T data() {
        return data;
    }

    public VKWebResult<T> data(T data) {
        this.data = data;
        return this;
    }

    public long requestCostMs() {
        return requestCostMs;
    }

    public VKWebResult<T> requestCostMs(long requestCostMs) {
        this.requestCostMs = requestCostMs;
        return this;
    }

    public String traceId() {
        return traceId;
    }

    public VKWebResult<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public long requestTime() {
        return requestTime;
    }

    public VKWebResult<T> requestTime(long requestTime) {
        this.requestTime = requestTime;
        return this;
    }

    public long responseTime() {
        return responseTime;
    }

    public VKWebResult<T> responseTime(long responseTime) {
        this.responseTime = responseTime;
        return this;
    }
}
