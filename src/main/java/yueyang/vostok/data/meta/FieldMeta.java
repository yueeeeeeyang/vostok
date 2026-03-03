package yueyang.vostok.data.meta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * 实体字段的元数据快照，包含列名映射、主键标记、版本控制、逻辑删除及可写性控制等信息。
 * 使用 MethodHandle 加速字段读写，回退时使用反射。
 */
public class FieldMeta {
    private final Field field;
    private final String columnName;
    /** 所属表名（来自 {@code @VKEntity.table}），用于字段加密的自动 columnKeyId 推导。 */
    private final String tableName;
    private final boolean id;
    private final boolean auto;
    private final boolean encrypted;
    private final String encryptionKeyId;
    /** 是否为乐观锁版本字段（@VKVersion）。 */
    private final boolean version;
    /** 是否为逻辑删除标志字段（@VKLogicDelete）。 */
    private final boolean logicDelete;
    /**
     * 已删除状态的标志值（类型已按字段类型转换）；仅 logicDelete=true 时有效。
     */
    private final Object deletedValue;
    /**
     * 正常（未删除）状态的标志值（类型已按字段类型转换）；仅 logicDelete=true 时有效。
     */
    private final Object normalValue;
    /** 是否参与 INSERT 语句，对应 @VKColumn.insertable。 */
    private final boolean insertable;
    /** 是否参与 UPDATE SET 子句，对应 @VKColumn.updatable。 */
    private final boolean updatable;
    /** 列是否允许为空，对应 @VKColumn.nullable；false 时自动建表生成 NOT NULL。 */
    private final boolean nullable;
    /** 字符串类型列的长度，对应 @VKColumn.length；自动建表用于 VARCHAR(length)。 */
    private final int length;
    /** 是否添加唯一约束，对应 @VKColumn.unique；true 时自动建表生成 UNIQUE。 */
    private final boolean unique;
    private final MethodHandle getter;
    private final MethodHandle setter;

    /**
     * 全参数构造器，由 MetaLoader 调用。
     *
     * @param field            Java 反射字段对象
     * @param columnName       数据库列名
     * @param tableName        所属表名（来自 @VKEntity.table），字段加密自动推导 columnKeyId 用
     * @param id               是否为主键字段
     * @param auto             是否为自增主键
     * @param encrypted        是否加密存储
     * @param encryptionKeyId  加密 keyId（空字符串表示使用默认 tableName-columnName）
     * @param version          是否为乐观锁版本字段
     * @param logicDelete      是否为逻辑删除字段
     * @param deletedValue     已删除状态值（类型已转换）
     * @param normalValue      正常状态值（类型已转换）
     * @param insertable       是否参与 INSERT
     * @param updatable        是否参与 UPDATE
     * @param nullable         列是否允许为空（DDL）
     * @param length           字符串类型列的最大长度（DDL）
     * @param unique           是否添加唯一约束（DDL）
     */
    public FieldMeta(Field field, String columnName, String tableName, boolean id, boolean auto,
                     boolean encrypted, String encryptionKeyId,
                     boolean version, boolean logicDelete,
                     Object deletedValue, Object normalValue,
                     boolean insertable, boolean updatable,
                     boolean nullable, int length, boolean unique) {
        this.field = field;
        this.columnName = columnName;
        this.tableName = tableName;
        this.id = id;
        this.auto = auto;
        this.encrypted = encrypted;
        this.encryptionKeyId = encryptionKeyId;
        this.version = version;
        this.logicDelete = logicDelete;
        this.deletedValue = deletedValue;
        this.normalValue = normalValue;
        this.insertable = insertable;
        this.updatable = updatable;
        this.nullable = nullable;
        this.length = length;
        this.unique = unique;
        this.field.setAccessible(true);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle g;
        MethodHandle s;
        try {
            g = lookup.unreflectGetter(field);
        } catch (IllegalAccessException e) {
            g = null;
        }
        try {
            s = lookup.unreflectSetter(field);
        } catch (IllegalAccessException e) {
            s = null;
        }
        this.getter = g;
        this.setter = s;
    }

    public Field getField() {
        return field;
    }

    public String getColumnName() {
        return columnName;
    }

    /** 所属表名（来自 {@code @VKEntity.table}），字段加密自动推导 columnKeyId 用。 */
    public String getTableName() {
        return tableName;
    }

    public boolean isId() {
        return id;
    }

    public boolean isAuto() {
        return auto;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public String getEncryptionKeyId() {
        return encryptionKeyId;
    }

    /** 是否为乐观锁版本字段。 */
    public boolean isVersion() {
        return version;
    }

    /** 是否为逻辑删除字段。 */
    public boolean isLogicDelete() {
        return logicDelete;
    }

    /** 已删除状态值（类型与字段类型一致）。 */
    public Object getDeletedValue() {
        return deletedValue;
    }

    /** 正常（未删除）状态值（类型与字段类型一致）。 */
    public Object getNormalValue() {
        return normalValue;
    }

    /** 是否参与 INSERT 语句。 */
    public boolean isInsertable() {
        return insertable;
    }

    /** 是否参与 UPDATE SET 子句。 */
    public boolean isUpdatable() {
        return updatable;
    }

    /** 列是否允许为空；false 时自动建表生成 NOT NULL 约束。 */
    public boolean isNullable() {
        return nullable;
    }

    /** 字符串类型列的最大长度；自动建表时用于 VARCHAR(length)。 */
    public int getLength() {
        return length;
    }

    /** 是否为唯一列；true 时自动建表生成 UNIQUE 约束。 */
    public boolean isUnique() {
        return unique;
    }

    public Object getValue(Object obj) {
        try {
            if (getter != null) {
                try {
                    return getter.invoke(obj);
                } catch (Throwable ignore) {
                    // fallback to reflection
                }
            }
            return field.get(obj);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to read field: " + field.getName(), e);
        }
    }

    public void setValue(Object obj, Object value) {
        try {
            if (setter != null) {
                try {
                    setter.invoke(obj, value);
                    return;
                } catch (Throwable ignore) {
                    // fallback to reflection
                }
            }
            field.set(obj, value);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to set field: " + field.getName(), e);
        }
    }
}
