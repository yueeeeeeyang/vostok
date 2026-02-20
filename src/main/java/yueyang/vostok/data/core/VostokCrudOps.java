package yueyang.vostok.data.core;

import yueyang.vostok.data.exception.VKErrorCode;
import yueyang.vostok.data.exception.VKException;
import yueyang.vostok.data.exception.VKExceptionTranslator;
import yueyang.vostok.data.jdbc.VKBatchDetailResult;
import yueyang.vostok.data.jdbc.VKBatchItemResult;
import yueyang.vostok.data.jdbc.VKBatchResult;
import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.data.meta.MetaRegistry;
import yueyang.vostok.data.query.VKAggregate;
import yueyang.vostok.data.query.VKCondition;
import yueyang.vostok.data.query.VKConditionGroup;
import yueyang.vostok.data.query.VKOperator;
import yueyang.vostok.data.query.VKQuery;
import yueyang.vostok.data.sql.SqlAndParams;
import yueyang.vostok.data.sql.SqlBuilder;
import yueyang.vostok.data.sql.SqlTemplate;
import yueyang.vostok.data.sql.SqlTemplateType;
import yueyang.vostok.data.tx.VKTransactionManager;
import yueyang.vostok.util.VKAssert;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CRUD / 查询相关操作。
 */
public final class VostokCrudOps {
    private VostokCrudOps() {
    }

    public static int insert(Object entity) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entity, "Entity is null");
        EntityMeta meta = MetaRegistry.get(entity.getClass());
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.INSERT);
        SqlAndParams sp = new SqlAndParams(tpl.getSql(), tpl.bindEntity(entity, VostokInternal.currentConfig()));

        if (meta.getIdField().isAuto()) {
            Object key = VostokInternal.executeInsert(sp);
            if (key != null) {
                VostokInternal.setGeneratedId(meta.getIdField(), entity, key);
            }
            return key != null ? 1 : 0;
        }

        return VostokInternal.executeUpdate(sp);
    }

    public static int batchInsert(List<?> entities) {
        return batchInsertDetail(entities).totalSuccess();
    }

    public static VKBatchDetailResult batchInsertDetail(List<?> entities) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entities, "Entities is null");
        VKAssert.isTrue(!entities.isEmpty(), "Entities is empty");

        if (!VKTransactionManager.inTransaction()) {
            return VostokTxOps.tx(() -> batchInsertDetailInternal(entities), yueyang.vostok.data.config.VKTxPropagation.REQUIRED,
                    yueyang.vostok.data.config.VKTxIsolation.DEFAULT, false);
        }
        return batchInsertDetailInternal(entities);
    }

    private static VKBatchDetailResult batchInsertDetailInternal(List<?> entities) {
        Class<?> entityClass = entities.get(0).getClass();
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.INSERT);
        String sql = tpl.getSql();

        List<Object[]> paramsList = new ArrayList<>();
        for (Object entity : entities) {
            VKAssert.isTrue(entity.getClass() == entityClass, "Mixed entity classes are not allowed");
            paramsList.add(tpl.bindEntity(entity, VostokInternal.currentConfig()));
        }

        List<VKBatchItemResult> items = new ArrayList<>();
        List<Object> allKeys = meta.getIdField().isAuto() ? new ArrayList<>() : Collections.emptyList();
        int baseIndex = 0;
        for (List<Object[]> chunk : VostokInternal.split(paramsList, VostokInternal.currentConfig().getBatchSize())) {
            try {
                VKBatchResult result = VostokInternal.currentExecutor().executeBatch(sql, chunk, meta.getIdField().isAuto());
                int[] counts = result.getCounts();
                for (int i = 0; i < counts.length; i++) {
                    Object key = meta.getIdField().isAuto() && i < result.getKeys().size() ? result.getKeys().get(i) : null;
                    items.add(new VKBatchItemResult(baseIndex + i, counts[i] >= 0, counts[i], key, null));
                    if (meta.getIdField().isAuto() && key != null) {
                        allKeys.add(key);
                    }
                }
            } catch (SQLException e) {
                try {
                    VKBatchDetailResult detail = VostokInternal.currentExecutor().executeBatchDetailedFallback(sql, chunk, meta.getIdField().isAuto());
                    for (VKBatchItemResult item : detail.getItems()) {
                        items.add(new VKBatchItemResult(baseIndex + item.getIndex(), item.isSuccess(), item.getCount(), item.getKey(), item.getError()));
                    }
                } catch (SQLException ex) {
                    for (int i = 0; i < chunk.size(); i++) {
                        items.add(new VKBatchItemResult(baseIndex + i, false, 0, null, ex.getMessage()));
                    }
                }
                VostokInternal.handleBatchError("SQL batch insert failed: " + sql, e);
            }
            baseIndex += chunk.size();
        }

        if (meta.getIdField().isAuto()) {
            VostokInternal.applyGeneratedKeys(meta.getIdField(), entities, allKeys);
        }
        return new VKBatchDetailResult(items);
    }

    public static int update(Object entity) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entity, "Entity is null");
        EntityMeta meta = MetaRegistry.get(entity.getClass());
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.UPDATE);
        return VostokInternal.executeUpdate(new SqlAndParams(tpl.getSql(), tpl.bindEntity(entity, VostokInternal.currentConfig())));
    }

    public static int batchUpdate(List<?> entities) {
        return batchUpdateDetail(entities).totalSuccess();
    }

    public static VKBatchDetailResult batchUpdateDetail(List<?> entities) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entities, "Entities is null");
        VKAssert.isTrue(!entities.isEmpty(), "Entities is empty");

        if (!VKTransactionManager.inTransaction()) {
            return VostokTxOps.tx(() -> batchUpdateDetailInternal(entities), yueyang.vostok.data.config.VKTxPropagation.REQUIRED,
                    yueyang.vostok.data.config.VKTxIsolation.DEFAULT, false);
        }
        return batchUpdateDetailInternal(entities);
    }

    private static VKBatchDetailResult batchUpdateDetailInternal(List<?> entities) {
        Class<?> entityClass = entities.get(0).getClass();
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.UPDATE);
        String sql = tpl.getSql();

        List<Object[]> paramsList = new ArrayList<>();
        for (Object entity : entities) {
            VKAssert.isTrue(entity.getClass() == entityClass, "Mixed entity classes are not allowed");
            paramsList.add(tpl.bindEntity(entity, VostokInternal.currentConfig()));
        }

        List<VKBatchItemResult> items = new ArrayList<>();
        int baseIndex = 0;
        for (List<Object[]> chunk : VostokInternal.split(paramsList, VostokInternal.currentConfig().getBatchSize())) {
            try {
                VKBatchResult result = VostokInternal.currentExecutor().executeBatch(sql, chunk, false);
                int[] counts = result.getCounts();
                for (int i = 0; i < counts.length; i++) {
                    items.add(new VKBatchItemResult(baseIndex + i, counts[i] >= 0, counts[i], null, null));
                }
            } catch (SQLException e) {
                try {
                    VKBatchDetailResult detail = VostokInternal.currentExecutor().executeBatchDetailedFallback(sql, chunk, false);
                    for (VKBatchItemResult item : detail.getItems()) {
                        items.add(new VKBatchItemResult(baseIndex + item.getIndex(), item.isSuccess(), item.getCount(), null, item.getError()));
                    }
                } catch (SQLException ex) {
                    for (int i = 0; i < chunk.size(); i++) {
                        items.add(new VKBatchItemResult(baseIndex + i, false, 0, null, ex.getMessage()));
                    }
                }
                VostokInternal.handleBatchError("SQL batch update failed: " + sql, e);
            }
            baseIndex += chunk.size();
        }

        return new VKBatchDetailResult(items);
    }

    public static int delete(Class<?> entityClass, Object idValue) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.DELETE_BY_ID);
        return VostokInternal.executeUpdate(new SqlAndParams(tpl.getSql(), tpl.bindId(idValue)));
    }

    public static int batchDelete(Class<?> entityClass, List<?> idValues) {
        return batchDeleteDetail(entityClass, idValues).totalSuccess();
    }

    public static VKBatchDetailResult batchDeleteDetail(Class<?> entityClass, List<?> idValues) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        VKAssert.notNull(idValues, "Id list is null");
        VKAssert.isTrue(!idValues.isEmpty(), "Id list is empty");

        EntityMeta meta = MetaRegistry.get(entityClass);
        List<VKBatchItemResult> items = new ArrayList<>();
        int baseIndex = 0;
        for (List<?> chunk : VostokInternal.split(idValues, VostokInternal.currentConfig().getBatchSize())) {
            String sql = VostokInternal.buildDeleteInSql(meta, chunk.size());
            try {
                int count = VostokInternal.currentExecutor().executeUpdate(sql, chunk.toArray());
                for (int i = 0; i < chunk.size(); i++) {
                    items.add(new VKBatchItemResult(baseIndex + i, count > 0, 1, null, null));
                }
            } catch (SQLException e) {
                for (int i = 0; i < chunk.size(); i++) {
                    items.add(new VKBatchItemResult(baseIndex + i, false, 0, null, e.getMessage()));
                }
                VostokInternal.handleBatchError("SQL batch delete failed: " + sql, e);
            }
            baseIndex += chunk.size();
        }
        return new VKBatchDetailResult(items);
    }

    public static <T> T findById(Class<T> entityClass, Object idValue) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.SELECT_BY_ID);
        return VostokInternal.executeQueryOne(meta, new SqlAndParams(tpl.getSql(), tpl.bindId(idValue)));
    }

    public static <T> List<T> findAll(Class<T> entityClass) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.SELECT_ALL);
        return VostokInternal.executeQueryList(meta, new SqlAndParams(tpl.getSql(), new Object[0]));
    }

    public static <T> List<T> query(Class<T> entityClass, VKQuery query) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        validateEncryptedQuery(meta, query);
        SqlAndParams sp = SqlBuilder.buildSelect(meta, query, VostokInternal.currentDialect());
        return VostokInternal.executeQueryList(meta, sp);
    }

    public static <T> List<T> queryColumns(Class<T> entityClass, VKQuery query, String... fields) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        VKAssert.notNull(fields, "Fields is null");
        VKAssert.isTrue(fields.length > 0, "Fields is empty");

        EntityMeta meta = MetaRegistry.get(entityClass);
        validateEncryptedQuery(meta, query);
        List<FieldMeta> projection = new ArrayList<>();
        for (String field : fields) {
            FieldMeta fm = meta.getFieldByName(field);
            VKAssert.notNull(fm, "Unknown field: " + field);
            projection.add(fm);
        }

        SqlAndParams sp = SqlBuilder.buildSelect(meta, projection, query, VostokInternal.currentDialect());
        try {
            return VostokInternal.currentExecutor().queryList(meta, projection, sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    public static List<Object[]> aggregate(Class<?> entityClass, VKQuery query, VKAggregate... aggregates) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        VKAssert.notNull(query, "Query is null");
        query.selectAggregates(aggregates);
        EntityMeta meta = MetaRegistry.get(entityClass);
        validateEncryptedQuery(meta, query);
        SqlAndParams sp = SqlBuilder.buildSelect(meta, query, VostokInternal.currentDialect());
        try {
            return VostokInternal.currentExecutor().queryRows(sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    public static long count(Class<?> entityClass, VKQuery query) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        validateEncryptedQuery(meta, query);
        SqlAndParams sp = SqlBuilder.buildCount(meta, query, VostokInternal.currentDialect());
        try {
            Object value = VostokInternal.currentExecutor().queryScalar(sp.getSql(), sp.getParams());
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return value == null ? 0L : Long.parseLong(value.toString());
        } catch (Exception e) {
            if (e instanceof SQLException) {
                throw VKExceptionTranslator.translate(sp.getSql(), (SQLException) e);
            }
            throw new VKException(VKErrorCode.SQL_ERROR, "SQL count failed: " + sp.getSql(), e);
        }
    }

    private static void validateEncryptedQuery(EntityMeta meta, VKQuery query) {
        if (query == null) {
            return;
        }
        validateConditionGroups(meta, query.getGroups(), "where");
        validateConditionGroups(meta, query.getHaving(), "having");
        for (var order : query.getOrders()) {
            FieldMeta field = meta.getFieldByName(order.getField());
            if (field != null && field.isEncrypted()) {
                throw new VKException(VKErrorCode.INVALID_ARGUMENT, "Encrypted field does not support orderBy: " + order.getField());
            }
        }
        for (String groupByField : query.getGroupBy()) {
            FieldMeta field = meta.getFieldByName(groupByField);
            if (field != null && field.isEncrypted()) {
                throw new VKException(VKErrorCode.INVALID_ARGUMENT, "Encrypted field does not support groupBy: " + groupByField);
            }
        }
        for (VKAggregate aggregate : query.getAggregates()) {
            String fieldName = aggregate.getField();
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            FieldMeta field = meta.getFieldByName(fieldName);
            if (field != null && field.isEncrypted()) {
                throw new VKException(VKErrorCode.INVALID_ARGUMENT, "Encrypted field does not support aggregate: " + fieldName);
            }
        }
    }

    private static void validateConditionGroups(EntityMeta meta, List<VKConditionGroup> groups, String stage) {
        for (VKConditionGroup group : groups) {
            for (VKCondition condition : group.getConditions()) {
                String fieldName = condition.getField();
                if (fieldName == null || fieldName.isBlank()) {
                    continue;
                }
                FieldMeta field = meta.getFieldByName(fieldName);
                if (field == null || !field.isEncrypted()) {
                    continue;
                }
                VKOperator op = condition.getOp();
                boolean allowed = op == VKOperator.IS_NULL || op == VKOperator.IS_NOT_NULL;
                if (!allowed) {
                    throw new VKException(VKErrorCode.INVALID_ARGUMENT,
                            "Encrypted field only supports IS_NULL/IS_NOT_NULL in " + stage + ": " + fieldName);
                }
            }
        }
    }
}
