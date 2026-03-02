package yueyang.vostok.data.sql;

import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.util.VKAssert;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * SQL 模板构建器，为每种操作类型（INSERT / UPDATE / DELETE_BY_ID / SELECT_BY_ID / SELECT_ALL）
 * 生成对应的 {@link SqlTemplate}，并自动处理以下特性：
 *
 * <ul>
 *   <li><b>乐观锁（@VKVersion）</b>：UPDATE 生成 {@code version = version + 1}，
 *       WHERE 追加 {@code AND version = ?}。</li>
 *   <li><b>逻辑删除（@VKLogicDelete）</b>：DELETE_BY_ID 转换为软删 UPDATE；
 *       SELECT_BY_ID / SELECT_ALL 自动追加 normalValue 过滤条件。</li>
 *   <li><b>insertable / updatable</b>：跳过对应标志为 false 的字段。</li>
 * </ul>
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

    /**
     * 构建 INSERT 模板。
     *
     * <p>跳过条件：自增主键（auto=true）、insertable=false 的字段。
     * 版本字段：包含在 INSERT 中，bindEntity 时若值为 null 自动初始化为 0。
     * 逻辑删除字段：包含在 INSERT 中，bindEntity 时若值为 null 自动初始化为 normalValue。
     */
    private static SqlTemplate buildInsert(EntityMeta meta) {
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        List<FieldMeta> fields = new ArrayList<>();
        for (FieldMeta field : meta.getFields()) {
            // 跳过自增主键
            if (field.isId() && field.isAuto()) {
                continue;
            }
            // 跳过 insertable=false 的字段
            if (!field.isInsertable()) {
                continue;
            }
            columns.add(field.getColumnName());
            placeholders.add("?");
            fields.add(field);
        }
        VKAssert.isTrue(!fields.isEmpty(), "No insertable fields for: " + meta.getEntityClass().getName());
        String sql = "INSERT INTO " + meta.getTableName() + " (" + columns + ") VALUES (" + placeholders + ")";
        // versionField 引用传入，供 bindEntity 在值为 null 时初始化为 0
        return new SqlTemplate(sql, fields, meta.getIdField(),
                meta.getVersionField(), null, null, false, false);
    }

    /**
     * 构建 UPDATE 模板。
     *
     * <p>跳过条件：主键字段、updatable=false 的字段、版本字段（版本字段由框架特殊处理）。
     * 版本字段：SET 子句添加 {@code version = version + 1}（数据库侧原子递增）；
     *           WHERE 子句追加 {@code AND version = ?}（旧版本值由 bindEntity 追加）。
     */
    private static SqlTemplate buildUpdate(EntityMeta meta) {
        StringJoiner sets = new StringJoiner(", ");
        List<FieldMeta> fields = new ArrayList<>();
        for (FieldMeta field : meta.getFields()) {
            // 跳过主键
            if (field.isId()) {
                continue;
            }
            // 跳过 updatable=false 的字段
            if (!field.isUpdatable()) {
                continue;
            }
            // 版本字段：不加入 SET ? 绑定列表，而是在 SQL 中直接写 version = version + 1
            if (field.isVersion()) {
                sets.add(field.getColumnName() + " = " + field.getColumnName() + " + 1");
                // 注意：版本字段不加入 fields 列表（不参与 bindEntity 的普通绑定）
                continue;
            }
            sets.add(field.getColumnName() + " = ?");
            fields.add(field);
        }
        VKAssert.isTrue(!sets.toString().isEmpty(), "No updatable fields for: " + meta.getEntityClass().getName());
        FieldMeta idField = meta.getIdField();
        FieldMeta vf = meta.getVersionField();
        // WHERE 子句：id = ? [AND version = ?]
        StringBuilder whereSb = new StringBuilder(" WHERE ").append(idField.getColumnName()).append(" = ?");
        if (vf != null) {
            whereSb.append(" AND ").append(vf.getColumnName()).append(" = ?");
        }
        String sql = "UPDATE " + meta.getTableName() + " SET " + sets + whereSb;
        // appendId=true：bindEntity 末尾追加 id 值，若有版本字段再追加旧版本值
        return new SqlTemplate(sql, fields, idField, vf, null, null, true, false);
    }

    /**
     * 构建 DELETE_BY_ID 模板。
     *
     * <p>若实体标记了 {@code @VKLogicDelete}，则生成软删 UPDATE：
     * {@code UPDATE table SET del_col = ? WHERE id = ?}，bindId 前置 deletedValue。
     * 否则生成物理删除：{@code DELETE FROM table WHERE id = ?}。
     */
    private static SqlTemplate buildDelete(EntityMeta meta) {
        FieldMeta ld = meta.getLogicDeleteField();
        if (ld != null) {
            // 逻辑删除：UPDATE table SET del_col = ? WHERE id = ?
            String sql = "UPDATE " + meta.getTableName()
                    + " SET " + ld.getColumnName() + " = ?"
                    + " WHERE " + meta.getIdField().getColumnName() + " = ?";
            // beforeIdParams=[deletedValue]，bindId 返回 [deletedValue, id]
            return new SqlTemplate(sql, List.of(), meta.getIdField(),
                    null, new Object[]{ld.getDeletedValue()}, null, false, true);
        }
        // 物理删除
        String sql = "DELETE FROM " + meta.getTableName()
                + " WHERE " + meta.getIdField().getColumnName() + " = ?";
        return new SqlTemplate(sql, List.of(), meta.getIdField(), false, true);
    }

    /**
     * 构建 SELECT_BY_ID 模板。
     *
     * <p>若实体标记了 {@code @VKLogicDelete}，WHERE 追加 {@code AND del_col = ?}（normalValue），
     * bindId 返回 {@code [id, normalValue]}。
     */
    private static SqlTemplate buildSelectById(EntityMeta meta) {
        FieldMeta ld = meta.getLogicDeleteField();
        String baseWhere = " WHERE " + meta.getIdField().getColumnName() + " = ?";
        if (ld != null) {
            // SELECT ... WHERE id = ? AND del_col = ?
            String sql = "SELECT " + SqlBuilder.selectColumns(meta.getFields())
                    + " FROM " + meta.getTableName()
                    + baseWhere + " AND " + ld.getColumnName() + " = ?";
            // afterIdParams=[normalValue]，bindId 返回 [id, normalValue]
            return new SqlTemplate(sql, List.of(), meta.getIdField(),
                    null, null, new Object[]{ld.getNormalValue()}, false, true);
        }
        String sql = "SELECT " + SqlBuilder.selectColumns(meta.getFields())
                + " FROM " + meta.getTableName() + baseWhere;
        return new SqlTemplate(sql, List.of(), meta.getIdField(), false, true);
    }

    /**
     * 构建 SELECT_ALL 模板。
     *
     * <p>若实体标记了 {@code @VKLogicDelete}，WHERE 追加 {@code del_col = ?}（normalValue），
     * getStaticParams 返回 {@code [normalValue]}。
     */
    private static SqlTemplate buildSelectAll(EntityMeta meta) {
        FieldMeta ld = meta.getLogicDeleteField();
        if (ld != null) {
            // SELECT ... FROM table WHERE del_col = ?
            String sql = "SELECT " + SqlBuilder.selectColumns(meta.getFields())
                    + " FROM " + meta.getTableName()
                    + " WHERE " + ld.getColumnName() + " = ?";
            // afterIdParams=[normalValue]，getStaticParams() 返回 [normalValue]
            return new SqlTemplate(sql, List.of(), meta.getIdField(),
                    null, null, new Object[]{ld.getNormalValue()}, false, true);
        }
        String sql = "SELECT " + SqlBuilder.selectColumns(meta.getFields())
                + " FROM " + meta.getTableName();
        return new SqlTemplate(sql, List.of(), meta.getIdField(), false, false);
    }
}
