package yueyang.vostok;

import yueyang.vostok.data.VostokData;
import yueyang.vostok.web.VostokWeb;

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

    /**
     * Web 入口（统一对外 API）。
     */
    public static final class Web extends VostokWeb {
        private Web() {
        }
    }
}
