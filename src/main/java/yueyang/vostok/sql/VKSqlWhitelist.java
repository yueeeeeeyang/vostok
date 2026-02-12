package yueyang.vostok.sql;

import yueyang.vostok.util.VKAssert;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * raw/subquery SQL 白名单注册表（按模板精确匹配）。
 */
public final class VKSqlWhitelist {
    private static final String DEFAULT_DS = "default";
    private static final Map<String, Set<String>> RAW_SQL = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> SUBQUERY_SQL = new ConcurrentHashMap<>();
    private static volatile Supplier<String> DATA_SOURCE_NAME = () -> DEFAULT_DS;

    private VKSqlWhitelist() {
    }

    
    public static void registerRaw(String... sqls) {
        registerRaw(DEFAULT_DS, sqls);
    }

    
    public static void registerRaw(String dataSourceName, String[] sqls) {
        register(dataSourceName, sqls, RAW_SQL, "raw sql");
    }

    
    public static void registerSubquery(String... sqls) {
        registerSubquery(DEFAULT_DS, sqls);
    }

    
    public static void registerSubquery(String dataSourceName, String[] sqls) {
        register(dataSourceName, sqls, SUBQUERY_SQL, "subquery sql");
    }

    
    public static boolean allowRaw(String sql) {
        return allow(currentDataSource(), sql, RAW_SQL);
    }

    
    public static boolean allowSubquery(String sql) {
        return allow(currentDataSource(), sql, SUBQUERY_SQL);
    }

    
    public static void setDataSourceNameSupplier(Supplier<String> supplier) {
        VKAssert.notNull(supplier, "Data source name supplier is null");
        DATA_SOURCE_NAME = supplier;
    }

    
    private static void register(String dataSourceName, String[] sqls, Map<String, Set<String>> target, String desc) {
        VKAssert.notBlank(dataSourceName, "Data source name is blank");
        VKAssert.notNull(sqls, desc + " list is null");
        Set<String> set = target.computeIfAbsent(dataSourceName, key -> ConcurrentHashMap.newKeySet());
        for (String sql : sqls) {
            VKAssert.notBlank(sql, desc + " is blank");
            set.add(normalize(sql));
        }
    }

    
    private static boolean allow(String dataSourceName, String sql, Map<String, Set<String>> target) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        Set<String> set = target.get(dataSourceName);
        if (set == null) {
            return false;
        }
        return set.contains(normalize(sql));
    }

    
    private static String normalize(String sql) {
        String trimmed = sql.trim();
        String collapsed = trimmed.replaceAll("\\s+", " ");
        return collapsed.toLowerCase();
    }

    
    private static String currentDataSource() {
        String name = DATA_SOURCE_NAME.get();
        if (name == null || name.isBlank()) {
            return DEFAULT_DS;
        }
        return name;
    }
}
