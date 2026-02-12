package yueyang.vostok.data.ddl;

import yueyang.vostok.data.exception.VKException;
import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.data.pool.VKDataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
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
