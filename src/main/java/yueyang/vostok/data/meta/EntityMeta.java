package yueyang.vostok.data.meta;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityMeta {
    private final Class<?> entityClass;
    private final String tableName;
    private final FieldMeta idField;
    private final List<FieldMeta> fields;
    private final Map<String, FieldMeta> fieldMap;

    public EntityMeta(Class<?> entityClass, String tableName, FieldMeta idField, List<FieldMeta> fields) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.idField = idField;
        this.fields = fields;
        this.fieldMap = new HashMap<>();
        for (FieldMeta field : fields) {
            fieldMap.put(field.getField().getName(), field);
        }
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
}
