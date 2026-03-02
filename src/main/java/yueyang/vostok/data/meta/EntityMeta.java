package yueyang.vostok.data.meta;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体类的元数据快照（不可变），包含表名、主键、所有字段及特殊字段（版本、逻辑删除）的元数据。
 * 由 MetaLoader 构建，缓存于 MetaRegistry 中。
 */
public class EntityMeta {
    private final Class<?> entityClass;
    private final String tableName;
    private final FieldMeta idField;
    private final List<FieldMeta> fields;
    private final Map<String, FieldMeta> fieldMap;
    /** 乐观锁版本字段，不存在时为 null。 */
    private final FieldMeta versionField;
    /** 逻辑删除标志字段，不存在时为 null。 */
    private final FieldMeta logicDeleteField;

    public EntityMeta(Class<?> entityClass, String tableName, FieldMeta idField, List<FieldMeta> fields) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.idField = idField;
        this.fields = fields;
        this.fieldMap = new HashMap<>();
        FieldMeta vf = null;
        FieldMeta ldf = null;
        for (FieldMeta field : fields) {
            fieldMap.put(field.getField().getName(), field);
            // 扫描特殊字段
            if (field.isVersion()) {
                vf = field;
            }
            if (field.isLogicDelete()) {
                ldf = field;
            }
        }
        this.versionField = vf;
        this.logicDeleteField = ldf;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public FieldMeta getIdField() {
        return idField;
    }

    public List<FieldMeta> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public FieldMeta getFieldByName(String name) {
        return fieldMap.get(name);
    }

    /**
     * 获取乐观锁版本字段元数据，若实体未标记 {@code @VKVersion} 则返回 null。
     */
    public FieldMeta getVersionField() {
        return versionField;
    }

    /**
     * 获取逻辑删除标志字段元数据，若实体未标记 {@code @VKLogicDelete} 则返回 null。
     */
    public FieldMeta getLogicDeleteField() {
        return logicDeleteField;
    }
}
