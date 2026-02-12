package yueyang.vostok.data.sql;

import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.util.VKAssert;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * SQL 模板构建器（与 SqlBuilder 保持一致）。
 */
public final class SqlTemplateBuilder {
    private SqlTemplateBuilder() {
    }

    
    public static SqlTemplate build(EntityMeta meta, SqlTemplateType type) {
        VKAssert.notNull(meta, "EntityMeta is null");
        VKAssert.notNull(type, "SqlTemplateType is null");
        switch (type) {
            case INSERT:
                return buildInsert(meta);
            case UPDATE:
                return buildUpdate(meta);
            case DELETE_BY_ID:
                return buildDelete(meta);
            case SELECT_BY_ID:
                return buildSelectById(meta);
            case SELECT_ALL:
                return buildSelectAll(meta);
            default:
                throw new IllegalArgumentException("Unsupported template type: " + type);
        }
    }

    
    private static SqlTemplate buildInsert(EntityMeta meta) {
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        List<FieldMeta> fields = new ArrayList<>();
        for (FieldMeta field : meta.getFields()) {
            if (field.isId() && field.isAuto()) {
                continue;
            }
            columns.add(field.getColumnName());
            placeholders.add("?");
            fields.add(field);
        }
        VKAssert.isTrue(!fields.isEmpty(), "No insertable fields for: " + meta.getEntityClass().getName());
        String sql = "INSERT INTO " + meta.getTableName() + " (" + columns + ") VALUES (" + placeholders + ")";
        return new SqlTemplate(sql, fields, meta.getIdField(), false, false);
    }

    
    private static SqlTemplate buildUpdate(EntityMeta meta) {
        StringJoiner sets = new StringJoiner(", ");
        List<FieldMeta> fields = new ArrayList<>();
        for (FieldMeta field : meta.getFields()) {
            if (field.isId()) {
                continue;
            }
            sets.add(field.getColumnName() + " = ?");
            fields.add(field);
        }
        VKAssert.isTrue(!fields.isEmpty(), "No updatable fields for: " + meta.getEntityClass().getName());
        FieldMeta idField = meta.getIdField();
        String sql = "UPDATE " + meta.getTableName() + " SET " + sets + " WHERE " + idField.getColumnName() + " = ?";
        return new SqlTemplate(sql, fields, idField, true, false);
    }

    
    private static SqlTemplate buildDelete(EntityMeta meta) {
        String sql = "DELETE FROM " + meta.getTableName() + " WHERE " + meta.getIdField().getColumnName() + " = ?";
        return new SqlTemplate(sql, List.of(), meta.getIdField(), false, true);
    }

    
    private static SqlTemplate buildSelectById(EntityMeta meta) {
        String sql = "SELECT " + SqlBuilder.selectColumns(meta.getFields()) + " FROM " + meta.getTableName()
                + " WHERE " + meta.getIdField().getColumnName() + " = ?";
        return new SqlTemplate(sql, List.of(), meta.getIdField(), false, true);
    }

    
    private static SqlTemplate buildSelectAll(EntityMeta meta) {
        String sql = "SELECT " + SqlBuilder.selectColumns(meta.getFields()) + " FROM " + meta.getTableName();
        return new SqlTemplate(sql, List.of(), meta.getIdField(), false, false);
    }
}
