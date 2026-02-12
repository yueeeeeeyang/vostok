package yueyang.vostok.core;

import yueyang.vostok.exception.VKErrorCode;
import yueyang.vostok.exception.VKException;
import yueyang.vostok.exception.VKExceptionTranslator;
import yueyang.vostok.jdbc.VKBatchDetailResult;
import yueyang.vostok.jdbc.VKBatchItemResult;
import yueyang.vostok.jdbc.VKBatchResult;
import yueyang.vostok.meta.EntityMeta;
import yueyang.vostok.meta.FieldMeta;
import yueyang.vostok.meta.MetaRegistry;
import yueyang.vostok.query.VKAggregate;
import yueyang.vostok.query.VKQuery;
import yueyang.vostok.sql.SqlAndParams;
import yueyang.vostok.sql.SqlBuilder;
import yueyang.vostok.sql.SqlTemplate;
import yueyang.vostok.sql.SqlTemplateType;
import yueyang.vostok.util.VKAssert;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CRUD / 查询相关操作。
 */
final class VostokCrudOps {
    private VostokCrudOps() {
    }

    static int insert(Object entity) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entity, "Entity is null");
        EntityMeta meta = MetaRegistry.get(entity.getClass());
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.INSERT);
        SqlAndParams sp = new SqlAndParams(tpl.getSql(), tpl.bindEntity(entity));

        if (meta.getIdField().isAuto()) {
            Object key = VostokInternal.executeInsert(sp);
            if (key != null) {
                VostokInternal.setGeneratedId(meta.getIdField(), entity, key);
            }
            return key != null ? 1 : 0;
        }

        return VostokInternal.executeUpdate(sp);
    }

    static int batchInsert(List<?> entities) {
        return batchInsertDetail(entities).totalSuccess();
    }

    static VKBatchDetailResult batchInsertDetail(List<?> entities) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entities, "Entities is null");
        VKAssert.isTrue(!entities.isEmpty(), "Entities is empty");

        Class<?> entityClass = entities.get(0).getClass();
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.INSERT);
        String sql = tpl.getSql();

        List<Object[]> paramsList = new ArrayList<>();
        for (Object entity : entities) {
            VKAssert.isTrue(entity.getClass() == entityClass, "Mixed entity classes are not allowed");
            paramsList.add(tpl.bindEntity(entity));
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

    static int update(Object entity) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entity, "Entity is null");
        EntityMeta meta = MetaRegistry.get(entity.getClass());
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.UPDATE);
        return VostokInternal.executeUpdate(new SqlAndParams(tpl.getSql(), tpl.bindEntity(entity)));
    }

    static int batchUpdate(List<?> entities) {
        return batchUpdateDetail(entities).totalSuccess();
    }

    static VKBatchDetailResult batchUpdateDetail(List<?> entities) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entities, "Entities is null");
        VKAssert.isTrue(!entities.isEmpty(), "Entities is empty");

        Class<?> entityClass = entities.get(0).getClass();
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.UPDATE);
        String sql = tpl.getSql();

        List<Object[]> paramsList = new ArrayList<>();
        for (Object entity : entities) {
            VKAssert.isTrue(entity.getClass() == entityClass, "Mixed entity classes are not allowed");
            paramsList.add(tpl.bindEntity(entity));
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

    static int delete(Class<?> entityClass, Object idValue) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.DELETE_BY_ID);
        return VostokInternal.executeUpdate(new SqlAndParams(tpl.getSql(), tpl.bindId(idValue)));
    }

    static int batchDelete(Class<?> entityClass, List<?> idValues) {
        return batchDeleteDetail(entityClass, idValues).totalSuccess();
    }

    static VKBatchDetailResult batchDeleteDetail(Class<?> entityClass, List<?> idValues) {
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

    static <T> T findById(Class<T> entityClass, Object idValue) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.SELECT_BY_ID);
        return VostokInternal.executeQueryOne(meta, new SqlAndParams(tpl.getSql(), tpl.bindId(idValue)));
    }

    static <T> List<T> findAll(Class<T> entityClass) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlTemplate tpl = VostokInternal.currentTemplateCache().get(meta, SqlTemplateType.SELECT_ALL);
        return VostokInternal.executeQueryList(meta, new SqlAndParams(tpl.getSql(), new Object[0]));
    }

    static <T> List<T> query(Class<T> entityClass, VKQuery query) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlAndParams sp = SqlBuilder.buildSelect(meta, query);
        return VostokInternal.executeQueryList(meta, sp);
    }

    static <T> List<T> queryColumns(Class<T> entityClass, VKQuery query, String... fields) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        VKAssert.notNull(fields, "Fields is null");
        VKAssert.isTrue(fields.length > 0, "Fields is empty");

        EntityMeta meta = MetaRegistry.get(entityClass);
        List<FieldMeta> projection = new ArrayList<>();
        for (String field : fields) {
            FieldMeta fm = meta.getFieldByName(field);
            VKAssert.notNull(fm, "Unknown field: " + field);
            projection.add(fm);
        }

        SqlAndParams sp = SqlBuilder.buildSelect(meta, projection, query);
        try {
            return VostokInternal.currentExecutor().queryList(meta, projection, sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    static List<Object[]> aggregate(Class<?> entityClass, VKQuery query, VKAggregate... aggregates) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        VKAssert.notNull(query, "Query is null");
        query.selectAggregates(aggregates);
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlAndParams sp = SqlBuilder.buildSelect(meta, query);
        try {
            return VostokInternal.currentExecutor().queryRows(sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    static long count(Class<?> entityClass, VKQuery query) {
        VostokInternal.ensureInit();
        VKAssert.notNull(entityClass, "Entity class is null");
        EntityMeta meta = MetaRegistry.get(entityClass);
        SqlAndParams sp = SqlBuilder.buildCount(meta, query);
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
}
