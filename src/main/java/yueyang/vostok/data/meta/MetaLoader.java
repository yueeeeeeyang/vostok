package yueyang.vostok.data.meta;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKLogicDelete;
import yueyang.vostok.data.annotation.VKVersion;
import yueyang.vostok.util.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.data.annotation.VKIgnore;
import yueyang.vostok.util.VKAssert;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 实体元数据加载器，通过反射解析 {@code @VKEntity} 类的所有字段注解，
 * 构建不可变的 {@link EntityMeta} 快照，供 CRUD 操作使用。
 *
 * <p>解析规则：
 * <ul>
 *   <li>{@code @VKIgnore}：跳过，不参与任何持久化。</li>
 *   <li>{@code @VKId}：标记主键，每个实体恰好一个。</li>
 *   <li>{@code @VKVersion}：乐观锁版本字段，每个实体最多一个，必须为数值类型。</li>
 *   <li>{@code @VKLogicDelete}：逻辑删除字段，每个实体最多一个。</li>
 *   <li>{@code @VKColumn}：覆盖列名及 insertable / updatable / encrypted 等属性。</li>
 *   <li>无注解的普通字段：列名默认与字段名相同，insertable / updatable 均为 true。</li>
 * </ul>
 */
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
        FieldMeta versionField = null;
        FieldMeta logicDeleteField = null;
        List<FieldMeta> fields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(VKIgnore.class)) {
                continue;
            }

            VKId id = field.getAnnotation(VKId.class);
            VKColumn column = field.getAnnotation(VKColumn.class);
            VKVersion version = field.getAnnotation(VKVersion.class);
            VKLogicDelete logicDelete = field.getAnnotation(VKLogicDelete.class);

            String columnName = column != null ? column.name() : field.getName();
            boolean encrypted = column != null && column.encrypted();
            String keyId = column != null ? column.keyId() : "";
            // insertable / updatable：主键字段的 insertable / updatable 由 @VKId.auto 控制，
            // 普通字段默认均为 true，可通过 @VKColumn 覆盖。
            boolean insertable = (id != null) || (column == null) || column.insertable();
            boolean updatable = (id == null) && ((column == null) || column.updatable());
            // nullable / length / unique：仅影响 DDL 自动建表，不影响运行时 CRUD。
            boolean nullable = (column == null) || column.nullable();
            int length = (column != null) ? column.length() : 255;
            boolean unique = column != null && column.unique();

            yueyang.vostok.util.VKNameValidator.validate(columnName, "Column name");

            boolean isId = id != null;
            boolean auto = id != null && id.auto();

            // 加密字段类型校验
            if (encrypted && field.getType() != String.class) {
                throw new yueyang.vostok.data.exception.VKMetaException(
                        "Encrypted field must be String type: " + clazz.getName() + "." + field.getName()
                );
            }

            // @VKVersion 校验：类型必须为数值型
            boolean isVersion = version != null;
            if (isVersion) {
                Class<?> t = field.getType();
                if (t != Long.class && t != long.class && t != Integer.class && t != int.class) {
                    throw new yueyang.vostok.data.exception.VKMetaException(
                            "@VKVersion field must be Long, long, Integer or int: "
                                    + clazz.getName() + "." + field.getName()
                    );
                }
                if (versionField != null) {
                    throw new yueyang.vostok.data.exception.VKMetaException(
                            "Multiple @VKVersion in class: " + clazz.getName()
                    );
                }
            }

            // @VKLogicDelete 校验：每个实体最多一个
            boolean isLogicDelete = logicDelete != null;
            Object deletedValueObj = null;
            Object normalValueObj = null;
            if (isLogicDelete) {
                if (logicDeleteField != null) {
                    throw new yueyang.vostok.data.exception.VKMetaException(
                            "Multiple @VKLogicDelete in class: " + clazz.getName()
                    );
                }
                // 将 String 注解值转换为字段对应的 Java 类型
                deletedValueObj = convertAnnotationValue(field, logicDelete.deletedValue(), clazz);
                normalValueObj = convertAnnotationValue(field, logicDelete.normalValue(), clazz);
            }

            FieldMeta meta = new FieldMeta(field, columnName, isId, auto, encrypted, keyId,
                    isVersion, isLogicDelete, deletedValueObj, normalValueObj,
                    insertable, updatable, nullable, length, unique);
            fields.add(meta);

            if (isId) {
                if (idField != null) {
                    throw new yueyang.vostok.data.exception.VKMetaException(
                            "Multiple @VKId in class: " + clazz.getName()
                    );
                }
                idField = meta;
            }
            if (isVersion) {
                versionField = meta;
            }
            if (isLogicDelete) {
                logicDeleteField = meta;
            }
        }

        if (idField == null) {
            throw new yueyang.vostok.data.exception.VKMetaException(
                    "Missing @VKId in class: " + clazz.getName()
            );
        }

        return new EntityMeta(clazz, table, idField, fields);
    }

    /**
     * 将 {@code @VKLogicDelete} 注解中的字符串值转换为字段的实际 Java 类型。
     * 支持 String、Integer/int、Long/long、Boolean/boolean。
     *
     * @param field      字段反射对象
     * @param strValue   注解中的字符串值
     * @param entityClass 实体类（用于错误提示）
     * @return 转换后的类型安全值
     */
    private static Object convertAnnotationValue(Field field, String strValue, Class<?> entityClass) {
        Class<?> type = field.getType();
        try {
            if (type == Integer.class || type == int.class) {
                return Integer.parseInt(strValue);
            }
            if (type == Long.class || type == long.class) {
                return Long.parseLong(strValue);
            }
            if (type == Boolean.class || type == boolean.class) {
                // 支持 "true"/"false" 以及 "1"/"0"
                return "true".equalsIgnoreCase(strValue) || "1".equals(strValue);
            }
            // 默认 String
            return strValue;
        } catch (NumberFormatException e) {
            throw new yueyang.vostok.data.exception.VKMetaException(
                    "@VKLogicDelete value \"" + strValue + "\" cannot be converted to "
                            + type.getSimpleName() + " in " + entityClass.getName() + "." + field.getName()
            );
        }
    }
}
