package yueyang.vostok.sql;

import yueyang.vostok.dialect.VKDialectManager;
import yueyang.vostok.meta.EntityMeta;
import yueyang.vostok.meta.FieldMeta;
import yueyang.vostok.query.VKAggregate;
import yueyang.vostok.query.VKCondition;
import yueyang.vostok.query.VKConditionGroup;
import yueyang.vostok.query.VKLogic;
import yueyang.vostok.query.VKOperator;
import yueyang.vostok.query.VKOrder;
import yueyang.vostok.query.VKQuery;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKNameValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class SqlBuilder {
    private SqlBuilder() {
    }

    public static SqlAndParams buildInsert(EntityMeta meta, Object entity) {
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");

        for (FieldMeta field : meta.getFields()) {
            if (field.isId() && field.isAuto()) {
                continue;
            }
            columns.add(field.getColumnName());
            placeholders.add("?");
        }
        Object[] params = buildInsertParams(meta, entity);

        String sql = "INSERT INTO " + meta.getTableName() + " (" + columns + ") VALUES (" + placeholders + ")";
        return new SqlAndParams(sql, params);
    }

    public static SqlAndParams buildUpdate(EntityMeta meta, Object entity) {
        StringJoiner sets = new StringJoiner(", ");

        for (FieldMeta field : meta.getFields()) {
            if (field.isId()) {
                continue;
            }
            sets.add(field.getColumnName() + " = ?");
        }
        Object[] params = buildUpdateParams(meta, entity);
        FieldMeta idField = meta.getIdField();

        String sql = "UPDATE " + meta.getTableName() + " SET " + sets + " WHERE " + idField.getColumnName() + " = ?";
        return new SqlAndParams(sql, params);
    }

    public static Object[] buildInsertParams(EntityMeta meta, Object entity) {
        List<Object> params = new ArrayList<>();
        for (FieldMeta field : meta.getFields()) {
            if (field.isId() && field.isAuto()) {
                continue;
            }
            params.add(field.getValue(entity));
        }
        VKAssert.isTrue(!params.isEmpty(), "No insertable fields for: " + meta.getEntityClass().getName());
        return params.toArray();
    }

    public static Object[] buildUpdateParams(EntityMeta meta, Object entity) {
        List<Object> params = new ArrayList<>();
        for (FieldMeta field : meta.getFields()) {
            if (field.isId()) {
                continue;
            }
            params.add(field.getValue(entity));
        }
        VKAssert.isTrue(!params.isEmpty(), "No updatable fields for: " + meta.getEntityClass().getName());
        FieldMeta idField = meta.getIdField();
        params.add(idField.getValue(entity));
        return params.toArray();
    }

    public static SqlAndParams buildDelete(EntityMeta meta, Object idValue) {
        String sql = "DELETE FROM " + meta.getTableName() + " WHERE " + meta.getIdField().getColumnName() + " = ?";
        return new SqlAndParams(sql, new Object[]{idValue});
    }

    public static SqlAndParams buildSelectById(EntityMeta meta, Object idValue) {
        String sql = "SELECT " + selectColumns(meta.getFields()) + " FROM " + meta.getTableName() + " WHERE "
                + meta.getIdField().getColumnName() + " = ?";
        return new SqlAndParams(sql, new Object[]{idValue});
    }

    public static String buildSelectAll(EntityMeta meta) {
        return "SELECT " + selectColumns(meta.getFields()) + " FROM " + meta.getTableName();
    }

    public static SqlAndParams buildSelect(EntityMeta meta, VKQuery query) {
        return buildSelect(meta, meta.getFields(), query);
    }

    public static SqlAndParams buildSelect(EntityMeta meta, List<FieldMeta> projection, VKQuery query) {
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();

        String selectPart = buildSelectPart(meta, projection, query);
        sb.append("SELECT ").append(selectPart).append(" FROM ").append(meta.getTableName());

        appendWhere(meta, query, sb, params);
        appendGroupBy(meta, query, sb);
        appendHaving(meta, query, sb, params);
        appendOrderBy(meta, query, sb);
        appendLimitOffset(query, sb);

        return new SqlAndParams(sb.toString(), params.toArray());
    }

    public static SqlAndParams buildCount(EntityMeta meta, VKQuery query) {
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();
        sb.append("SELECT COUNT(1) FROM ").append(meta.getTableName());
        appendWhere(meta, query, sb, params);
        return new SqlAndParams(sb.toString(), params.toArray());
    }

    private static String buildSelectPart(EntityMeta meta, List<FieldMeta> projection, VKQuery query) {
        if (query != null && !query.getAggregates().isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ");
            for (String field : query.getGroupBy()) {
                FieldMeta fm = meta.getFieldByName(field);
                VKAssert.notNull(fm, "Unknown groupBy field: " + field);
                joiner.add(fm.getColumnName());
            }
            for (VKAggregate agg : query.getAggregates()) {
                String expr = buildAggregateExpr(meta, agg);
                joiner.add(expr);
            }
            return joiner.toString();
        }
        return selectColumns(projection);
    }

    private static String buildAggregateExpr(EntityMeta meta, VKAggregate agg) {
        VKAssert.notNull(agg, "Aggregate is null");
        String func = agg.getType().name();
        String field = agg.getField();
        String expr;
        if (field == null || field.trim().isEmpty()) {
            expr = func + "(1)";
        } else {
            FieldMeta fm = meta.getFieldByName(field);
            VKAssert.notNull(fm, "Unknown aggregate field: " + field);
            expr = func + "(" + fm.getColumnName() + ")";
        }
        if (agg.getAlias() != null && !agg.getAlias().trim().isEmpty()) {
            VKNameValidator.validate(agg.getAlias(), "Aggregate alias");
            expr = expr + " AS " + agg.getAlias();
        }
        return expr;
    }

    public static String selectColumns(List<FieldMeta> fields) {
        StringJoiner columns = new StringJoiner(", ");
        for (FieldMeta field : fields) {
            columns.add(field.getColumnName());
        }
        return columns.toString();
    }

    private static void appendWhere(EntityMeta meta, VKQuery query, StringBuilder sb, List<Object> params) {
        if (query == null || query.getGroups().isEmpty()) {
            return;
        }
        sb.append(" WHERE ");
        StringJoiner groupJoiner = new StringJoiner(" AND ");
        for (VKConditionGroup group : query.getGroups()) {
            groupJoiner.add(buildGroup(meta, group, params));
        }
        sb.append(groupJoiner);
    }

    private static void appendGroupBy(EntityMeta meta, VKQuery query, StringBuilder sb) {
        if (query == null || query.getGroupBy().isEmpty()) {
            return;
        }
        sb.append(" GROUP BY ");
        StringJoiner joiner = new StringJoiner(", ");
        for (String field : query.getGroupBy()) {
            FieldMeta fm = meta.getFieldByName(field);
            VKAssert.notNull(fm, "Unknown groupBy field: " + field);
            joiner.add(fm.getColumnName());
        }
        sb.append(joiner);
    }

    private static void appendHaving(EntityMeta meta, VKQuery query, StringBuilder sb, List<Object> params) {
        if (query == null || query.getHaving().isEmpty()) {
            return;
        }
        sb.append(" HAVING ");
        StringJoiner groupJoiner = new StringJoiner(" AND ");
        for (VKConditionGroup group : query.getHaving()) {
            groupJoiner.add(buildGroup(meta, group, params));
        }
        sb.append(groupJoiner);
    }

    private static String buildGroup(EntityMeta meta, VKConditionGroup group, List<Object> params) {
        VKAssert.notNull(group, "Condition group is null");
        VKLogic logic = group.getLogic();
        VKAssert.notNull(logic, "Condition group logic is null");
        VKAssert.isTrue(!group.getConditions().isEmpty(), "Condition group is empty");

        StringJoiner joiner = new StringJoiner(logic == VKLogic.OR ? " OR " : " AND ");
        for (VKCondition condition : group.getConditions()) {
            joiner.add(buildCondition(meta, condition, params));
        }
        return "(" + joiner + ")";
    }

    private static String buildCondition(EntityMeta meta, VKCondition condition, List<Object> params) {
        VKAssert.notNull(condition, "Condition is null");
        VKOperator op = condition.getOp();
        VKAssert.notNull(op, "Condition operator is null");

        if (op == VKOperator.EXISTS || op == VKOperator.NOT_EXISTS) {
            VKAssert.notBlank(condition.getSubquery(), "Subquery is blank");
            VKAssert.isTrue(VKSqlWhitelist.allowSubquery(condition.getSubquery()), "Subquery not in whitelist");
            assertPlaceholders(condition.getSubquery(), condition.getSubParams());
            if (condition.getSubParams() != null && !condition.getSubParams().isEmpty()) {
                params.addAll(condition.getSubParams());
            }
            return (op == VKOperator.EXISTS ? "EXISTS (" : "NOT EXISTS (") + condition.getSubquery() + ")";
        }

        String columnExpr;
        if (condition.getRawExpr() != null && !condition.getRawExpr().trim().isEmpty()) {
            VKAssert.isTrue(VKSqlWhitelist.allowRaw(condition.getRawExpr()), "Raw SQL not in whitelist");
            columnExpr = condition.getRawExpr();
        } else {
            String field = condition.getField();
            VKAssert.notBlank(field, "Condition field is blank");
            FieldMeta fm = meta.getFieldByName(field);
            VKAssert.notNull(fm, "Unknown field: " + field);
            columnExpr = fm.getColumnName();
        }

        switch (op) {
            case EQ:
                requireValues(condition, 1);
                params.add(condition.getValues().get(0));
                return columnExpr + " = ?";
            case NE:
                requireValues(condition, 1);
                params.add(condition.getValues().get(0));
                return columnExpr + " <> ?";
            case GT:
                requireValues(condition, 1);
                params.add(condition.getValues().get(0));
                return columnExpr + " > ?";
            case GE:
                requireValues(condition, 1);
                params.add(condition.getValues().get(0));
                return columnExpr + " >= ?";
            case LT:
                requireValues(condition, 1);
                params.add(condition.getValues().get(0));
                return columnExpr + " < ?";
            case LE:
                requireValues(condition, 1);
                params.add(condition.getValues().get(0));
                return columnExpr + " <= ?";
            case LIKE:
                requireValues(condition, 1);
                params.add(condition.getValues().get(0));
                return columnExpr + " LIKE ?";
            case IN:
                if (condition.getSubquery() != null) {
                    VKAssert.notBlank(condition.getSubquery(), "Subquery is blank");
                    VKAssert.isTrue(VKSqlWhitelist.allowSubquery(condition.getSubquery()), "Subquery not in whitelist");
                    assertPlaceholders(condition.getSubquery(), condition.getSubParams());
                    if (condition.getSubParams() != null && !condition.getSubParams().isEmpty()) {
                        params.addAll(condition.getSubParams());
                    }
                    return columnExpr + " IN (" + condition.getSubquery() + ")";
                }
                VKAssert.isTrue(!condition.getValues().isEmpty(), "IN values are empty");
                StringJoiner inJoiner = new StringJoiner(", ", "(", ")");
                for (Object v : condition.getValues()) {
                    inJoiner.add("?");
                    params.add(v);
                }
                return columnExpr + " IN " + inJoiner;
            case NOT_IN:
                if (condition.getSubquery() != null) {
                    VKAssert.notBlank(condition.getSubquery(), "Subquery is blank");
                    VKAssert.isTrue(VKSqlWhitelist.allowSubquery(condition.getSubquery()), "Subquery not in whitelist");
                    assertPlaceholders(condition.getSubquery(), condition.getSubParams());
                    if (condition.getSubParams() != null && !condition.getSubParams().isEmpty()) {
                        params.addAll(condition.getSubParams());
                    }
                    return columnExpr + " NOT IN (" + condition.getSubquery() + ")";
                }
                VKAssert.isTrue(!condition.getValues().isEmpty(), "NOT IN values are empty");
                StringJoiner notInJoiner = new StringJoiner(", ", "(", ")");
                for (Object v : condition.getValues()) {
                    notInJoiner.add("?");
                    params.add(v);
                }
                return columnExpr + " NOT IN " + notInJoiner;
            case BETWEEN:
                requireValues(condition, 2);
                params.add(condition.getValues().get(0));
                params.add(condition.getValues().get(1));
                return columnExpr + " BETWEEN ? AND ?";
            case IS_NULL:
                return columnExpr + " IS NULL";
            case IS_NOT_NULL:
                return columnExpr + " IS NOT NULL";
            default:
                throw new yueyang.vostok.exception.VKArgumentException("Unsupported operator: " + op);
        }
    }

    private static void assertPlaceholders(String sql, List<Object> params) {
        int count = countPlaceholders(sql);
        int paramSize = params == null ? 0 : params.size();
        VKAssert.isTrue(count == paramSize, "Placeholder count does not match params");
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static void requireValues(VKCondition condition, int size) {
        VKAssert.isTrue(condition.getValues().size() == size, "Invalid values count for " + condition.getOp());
    }

    private static void appendOrderBy(EntityMeta meta, VKQuery query, StringBuilder sb) {
        if (query == null || query.getOrders().isEmpty()) {
            return;
        }
        sb.append(" ORDER BY ");
        StringJoiner joiner = new StringJoiner(", ");
        for (VKOrder order : query.getOrders()) {
            FieldMeta fm = meta.getFieldByName(order.getField());
            VKAssert.notNull(fm, "Unknown field: " + order.getField());
            joiner.add(fm.getColumnName() + (order.isAsc() ? " ASC" : " DESC"));
        }
        sb.append(joiner);
    }

    private static void appendLimitOffset(VKQuery query, StringBuilder sb) {
        if (query == null) {
            return;
        }
        VKDialectManager.getDialect().appendLimitOffset(sb, query.getLimit(), query.getOffset());
    }
}
