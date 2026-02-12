package yueyang.vostok;

import yueyang.vostok.data.core.VostokData;

/**
 * Vostok 统一入口。
 */
public final class Vostok {
    private Vostok() {
    }

    /**
     * 数据入口（统一对外 API）。
     */
    public static final class Data extends VostokData {
        private Data() {
        }
    }
}
