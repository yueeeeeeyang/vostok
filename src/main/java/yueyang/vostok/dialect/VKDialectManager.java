package yueyang.vostok.dialect;

import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.util.VKAssert;

public final class VKDialectManager {
    private static volatile VKDialect DIALECT = new MySqlDialect();

    private VKDialectManager() {
    }

    public static void init(DataSourceConfig config) {
        VKAssert.notNull(config, "DataSourceConfig is null");
        VKDialectType type = config.getDialect();
        if (type == null) {
            type = inferDialect(config.getUrl());
        }
        DIALECT = create(type);
    }

    public static VKDialect getDialect() {
        return DIALECT;
    }

    private static VKDialect create(VKDialectType type) {
        if (type == null) {
            return new MySqlDialect();
        }
        switch (type) {
            case POSTGRESQL:
                return new PostgreSqlDialect();
            case ORACLE:
                return new OracleDialect();
            case SQLSERVER:
                return new SqlServerDialect();
            case DB2:
                return new Db2Dialect();
            case MYSQL:
            default:
                return new MySqlDialect();
        }
    }

    private static VKDialectType inferDialect(String url) {
        if (url == null) {
            return VKDialectType.MYSQL;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:postgresql:")) {
            return VKDialectType.POSTGRESQL;
        }
        if (lower.startsWith("jdbc:oracle:")) {
            return VKDialectType.ORACLE;
        }
        if (lower.startsWith("jdbc:sqlserver:")) {
            return VKDialectType.SQLSERVER;
        }
        if (lower.startsWith("jdbc:db2:")) {
            return VKDialectType.DB2;
        }
        return VKDialectType.MYSQL;
    }
}
