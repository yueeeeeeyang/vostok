package yueyang.vostok.security.rule;

import yueyang.vostok.security.VKSecurityConfig;

public final class VKSecurityContext {
    private final String rawSql;
    private final String normalizedSql;
    private final String scannedSql;
    private final Object[] params;
    private final VKSecurityConfig config;

    public VKSecurityContext(String rawSql, String normalizedSql, String scannedSql, Object[] params, VKSecurityConfig config) {
        this.rawSql = rawSql;
        this.normalizedSql = normalizedSql;
        this.scannedSql = scannedSql;
        this.params = params == null ? new Object[0] : params.clone();
        this.config = config;
    }

    public String getRawSql() {
        return rawSql;
    }

    public String getNormalizedSql() {
        return normalizedSql;
    }

    public String getScannedSql() {
        return scannedSql;
    }

    public Object[] getParams() {
        return params.clone();
    }

    public VKSecurityConfig getConfig() {
        return config;
    }
}
