package yueyang.vostok.data.meta;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.data.annotation.VKIgnore;
import yueyang.vostok.util.VKAssert;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class MetaLoader {
    private MetaLoader() {
    }

    public static EntityMeta load(Class<?> clazz) {
        VKEntity entity = clazz.getAnnotation(VKEntity.class);
        VKAssert.notNull(entity, "Missing @VKEntity on class: " + clazz.getName());
        String table = entity.table();
        VKAssert.notBlank(table, "@VKEntity.table is blank for class: " + clazz.getName());
        yueyang.vostok.util.VKNameValidator.validate(table, "Table name");

        FieldMeta idField = null;
        List<FieldMeta> fields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(VKIgnore.class)) {
                continue;
            }

            VKId id = field.getAnnotation(VKId.class);
            VKColumn column = field.getAnnotation(VKColumn.class);
            String columnName = column != null ? column.name() : field.getName();
            boolean encrypted = column != null && column.encrypted();
            String keyId = column != null ? column.keyId() : "";
            yueyang.vostok.util.VKNameValidator.validate(columnName, "Column name");

            boolean isId = id != null;
            boolean auto = id != null && id.auto();
            if (encrypted && field.getType() != String.class) {
                throw new yueyang.vostok.data.exception.VKMetaException(
                        "Encrypted field must be String type: " + clazz.getName() + "." + field.getName()
                );
            }

            FieldMeta meta = new FieldMeta(field, columnName, isId, auto, encrypted, keyId);
            fields.add(meta);

            if (isId) {
                if (idField != null) {
                    throw new yueyang.vostok.data.exception.VKMetaException(
                            "Multiple @VKId in class: " + clazz.getName()
                    );
                }
                idField = meta;
            }
        }

        if (idField == null) {
            throw new yueyang.vostok.data.exception.VKMetaException(
                    "Missing @VKId in class: " + clazz.getName()
            );
        }

        return new EntityMeta(clazz, table, idField, fields);
    }
}
