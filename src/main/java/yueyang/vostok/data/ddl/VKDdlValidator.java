package yueyang.vostok.data.ddl;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.exception.VKException;
import yueyang.vostok.data.exception.VKMetaException;
import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.data.pool.VKDataSource;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.Vostok;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DDL 校验器：校验表与列是否存在。
 */
public final class VKDdlValidator {
    private VKDdlValidator() {
    }

    
    public static void validate(VKDataSource dataSource, List<EntityMeta> metas, String schema) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            for (EntityMeta em : metas) {
                String table = em.getTableName();
                if (!tableExists(meta, schema, table)) {
                    throw new yueyang.vostok.data.exception.VKMetaException("Table not found: " + table);
                }
                Set<String> columns = loadColumns(meta, schema, table);
                for (FieldMeta fm : em.getFields()) {
                    String col = fm.getColumnName().toLowerCase();
                    if (!columns.contains(col)) {
                        throw new yueyang.vostok.data.exception.VKMetaException("Column not found: " + table + "." + fm.getColumnName());
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof VKException) {
                throw (VKException) e;
            }
            throw new yueyang.vostok.data.exception.VKMetaException("DDL validation failed", e);
        }
    }

    public static void createMissingTables(VKDataSource dataSource, List<EntityMeta> metas, String schema, VKDataConfig config) {
        VKAssert.notNull(config, "VKDataConfig is null");
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            VKDialectType dialectType = resolveDialectType(config);
            for (EntityMeta em : metas) {
                String table = em.getTableName();
                if (tableExists(meta, schema, table)) {
                    continue;
                }
                String sql = buildCreateTableSql(em, schema, dialectType);
                Vostok.Log.info("Auto creating table: " + table);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
        } catch (Exception e) {
            if (e instanceof VKException) {
                throw (VKException) e;
            }
            throw new VKMetaException("DDL auto create failed", e);
        }
    }

    private static VKDialectType resolveDialectType(VKDataConfig config) {
        VKDialectType type = config.getDialect();
        if (type != null) {
            return type;
        }
        String url = config.getUrl();
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

    private static String buildCreateTableSql(EntityMeta meta, String schema, VKDialectType dialectType) {
        List<String> columns = new ArrayList<>();
        List<String> pk = new ArrayList<>();
        for (FieldMeta field : meta.getFields()) {
            String col = field.getColumnName();
            String colType = resolveSqlType(field.getField().getType(), dialectType, field.isAuto());
            StringBuilder def = new StringBuilder(col).append(" ").append(colType);
            if (field.isAuto()) {
                String auto = autoIncrementSuffix(dialectType, field.getField().getType());
                if (!auto.isEmpty()) {
                    def.append(" ").append(auto);
                }
            }
            if (field.isId() || field.getField().getType().isPrimitive()) {
                def.append(" NOT NULL");
            }
            columns.add(def.toString());
            if (field.isId()) {
                pk.add(col);
            }
        }
        if (!pk.isEmpty()) {
            columns.add("PRIMARY KEY (" + String.join(", ", pk) + ")");
        }
        String tableName = (schema == null || schema.isBlank()) ? meta.getTableName() : schema + "." + meta.getTableName();
        return "CREATE TABLE " + tableName + " (" + String.join(", ", columns) + ")";
    }

    private static String resolveSqlType(Class<?> type, VKDialectType dialectType, boolean auto) {
        if (auto) {
            if (dialectType == VKDialectType.POSTGRESQL) {
                if (type == long.class || type == Long.class) {
                    return "BIGSERIAL";
                }
                return "SERIAL";
            }
        }
        if (type == String.class) {
            return "VARCHAR(255)";
        }
        if (type == int.class || type == Integer.class) {
            return "INT";
        }
        if (type == long.class || type == Long.class) {
            return "BIGINT";
        }
        if (type == short.class || type == Short.class) {
            return "SMALLINT";
        }
        if (type == byte.class || type == Byte.class) {
            return "TINYINT";
        }
        if (type == boolean.class || type == Boolean.class) {
            switch (dialectType) {
                case ORACLE:
                    return "NUMBER(1)";
                case SQLSERVER:
                    return "BIT";
                case DB2:
                    return "SMALLINT";
                default:
                    return "BOOLEAN";
            }
        }
        if (type == float.class || type == Float.class) {
            return "FLOAT";
        }
        if (type == double.class || type == Double.class) {
            return "DOUBLE";
        }
        if (type.getName().equals("java.math.BigDecimal")) {
            return "DECIMAL(19,4)";
        }
        if (type.getName().equals("java.time.LocalDate") || type.getName().equals("java.sql.Date")) {
            return "DATE";
        }
        if (type.getName().equals("java.time.LocalDateTime") || type.getName().equals("java.sql.Timestamp")) {
            return "TIMESTAMP";
        }
        if (type == byte[].class) {
            return "BLOB";
        }
        if (type.isEnum()) {
            return "VARCHAR(64)";
        }
        throw new VKMetaException("Unsupported field type for DDL: " + type.getName());
    }

    private static String autoIncrementSuffix(VKDialectType dialectType, Class<?> type) {
        if (!(type == int.class || type == Integer.class || type == long.class || type == Long.class)) {
            return "";
        }
        switch (dialectType) {
            case ORACLE:
            case DB2:
                return "GENERATED BY DEFAULT AS IDENTITY";
            case SQLSERVER:
                return "IDENTITY(1,1)";
            case MYSQL:
            default:
                return "AUTO_INCREMENT";
        }
    }

    
    private static boolean tableExists(DatabaseMetaData meta, String schema, String table) throws Exception {
        try (ResultSet rs = meta.getTables(null, schema, table, null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getTables(null, schema, table.toUpperCase(), null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getTables(null, schema, table.toLowerCase(), null)) {
            return rs.next();
        }
    }

    
    private static Set<String> loadColumns(DatabaseMetaData meta, String schema, String table) throws Exception {
        Set<String> cols = new HashSet<>();
        try (ResultSet rs = meta.getColumns(null, schema, table, null)) {
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        if (!cols.isEmpty()) {
            return cols;
        }
        try (ResultSet rs = meta.getColumns(null, schema, table.toUpperCase(), null)) {
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return cols;
    }
}
