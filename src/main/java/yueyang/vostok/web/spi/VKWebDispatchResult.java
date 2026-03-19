package yueyang.vostok.web.spi;

import yueyang.vostok.web.http.VKResponse;

/**
 * HTTP 调度结果。
 *
 * 该对象承载协议无关的处理结果，具体引擎只需要负责把 response 写回到网络。
 */
public final class VKWebDispatchResult {
    private final VKResponse response;
    private final long costNs;
    private final boolean error;

    public VKWebDispatchResult(VKResponse response, long costNs, boolean error) {
        this.response = response;
        this.costNs = Math.max(0L, costNs);
        this.error = error;
    }

    public VKResponse response() {
        return response;
    }

    public long costNs() {
        return costNs;
    }

    public long costMs() {
        return costNs / 1_000_000L;
    }

    public boolean error() {
        return error;
    }
}
