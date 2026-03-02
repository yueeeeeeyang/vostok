package yueyang.vostok.data.sql;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.core.VKFieldCrypto;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.util.VKAssert;

import java.util.ArrayList;
import java.util.List;

/**
 * 预编译 SQL 模板，与实体元数据绑定，负责参数绑定。
 *
 * <p>参数绑定规则：
 * <ul>
 *   <li>{@link #bindEntity}：用于 INSERT / UPDATE，按字段顺序绑定非 id 字段，
 *       appendId=true 时在末尾追加主键值，若存在乐观锁版本字段则再追加旧版本值。</li>
 *   <li>{@link #bindId}：用于 DELETE_BY_ID / SELECT_BY_ID，
 *       若存在 beforeIdParams（逻辑删除 UPDATE），前置参数先于 id；
 *       若存在 afterIdParams（SELECT_BY_ID 逻辑删除过滤），追加于 id 之后。</li>
 *   <li>{@link #getStaticParams}：用于 SELECT_ALL，返回静态过滤参数（逻辑删除 normalValue）。</li>
 * </ul>
 */
public class SqlTemplate {
    private final String sql;
    /** SET 子句对应的字段列表（INSERT / UPDATE），不含 id 和版本字段。 */
    private final List<FieldMeta> fields;
    private final FieldMeta idField;
    /**
     * 乐观锁版本字段引用。
     * INSERT：bindEntity 检测到版本字段值为 null 时自动初始化为 0。
     * UPDATE：在 id 参数之后追加旧版本值，配合 "AND version = ?" WHERE 条件。
     */
    private final FieldMeta versionField;
    /**
     * bindId 中置于 id 参数之前的静态参数（逻辑删除 DELETE：[deletedValue, id]）。
     */
    private final Object[] beforeIdParams;
    /**
     * bindId 中置于 id 参数之后的静态参数（逻辑删除 SELECT_BY_ID：[id, normalValue]），
     * 同时作为 SELECT_ALL 的独立过滤参数（[normalValue]）。
     */
    private final Object[] afterIdParams;
    /** true 时 bindEntity 在末尾追加 id 和（若有）旧版本值，对应 UPDATE 模板。 */
    private final boolean appendId;
    /** true 时调用 bindEntity 会抛出异常，通过 bindId / getStaticParams 取参数。 */
    private final boolean idOnly;

    /**
     * 兼容旧签名的构造器（无版本字段、无逻辑删除）。
     */
    public SqlTemplate(String sql, List<FieldMeta> fields, FieldMeta idField,
                       boolean appendId, boolean idOnly) {
        this(sql, fields, idField, null, null, null, appendId, idOnly);
    }

    /**
     * 全参数构造器。
     *
     * @param sql            预编译 SQL 字符串（含 ? 占位符）
     * @param fields         SET / VALUES 子句绑定的字段列表
     * @param idField        主键字段元数据
     * @param versionField   乐观锁版本字段（null 表示不启用）
     * @param beforeIdParams bindId 前置静态参数（null 表示无）
     * @param afterIdParams  bindId 后置静态参数 / SELECT_ALL 静态过滤参数（null 表示无）
     * @param appendId       UPDATE 模板为 true，INSERT 为 false
     * @param idOnly         DELETE / SELECT_BY_ID / SELECT_ALL 模板为 true
     */
    public SqlTemplate(String sql, List<FieldMeta> fields, FieldMeta idField,
                       FieldMeta versionField, Object[] beforeIdParams, Object[] afterIdParams,
                       boolean appendId, boolean idOnly) {
        this.sql = sql;
        this.fields = fields;
        this.idField = idField;
        this.versionField = versionField;
        this.beforeIdParams = beforeIdParams;
        this.afterIdParams = afterIdParams;
        this.appendId = appendId;
        this.idOnly = idOnly;
    }

    public String getSql() {
        return sql;
    }

    /** 返回乐观锁版本字段，若实体无版本字段则为 null。 */
    public FieldMeta getVersionField() {
        return versionField;
    }

    /**
     * 绑定实体对象生成参数数组（INSERT / UPDATE）。
     *
     * <p>参数顺序：
     * <ol>
     *   <li>各非主键字段值（按 fields 顺序，版本字段 null 时初始化为 0）</li>
     *   <li>若 appendId=true：主键值</li>
     *   <li>若 appendId=true 且有版本字段：旧版本值（用于 WHERE version = ?）</li>
     * </ol>
     */
    public Object[] bindEntity(Object entity, VKDataConfig config) {
        VKAssert.notNull(entity, "Entity is null");
        if (idOnly) {
            throw new IllegalStateException("Template is id-only");
        }
        List<Object> params = new ArrayList<>();
        for (FieldMeta field : fields) {
            Object raw = field.getValue(entity);
            // INSERT：版本字段为 null 时自动初始化为 0
            if (field.isVersion() && raw == null) {
                raw = initVersionZero(field);
            }
            // INSERT：逻辑删除字段为 null 时自动初始化为 normalValue
            if (field.isLogicDelete() && raw == null) {
                raw = field.getNormalValue();
            }
            params.add(VKFieldCrypto.encryptWrite(field, raw, config));
        }
        if (appendId && idField != null) {
            // UPDATE：追加主键值（WHERE id = ?）
            params.add(idField.getValue(entity));
            // UPDATE with version：追加旧版本值（WHERE ... AND version = ?）
            if (versionField != null) {
                params.add(versionField.getValue(entity));
            }
        }
        return params.toArray();
    }

    /**
     * 绑定单个主键值（DELETE_BY_ID / SELECT_BY_ID）。
     *
     * <p>参数顺序：
     * <ol>
     *   <li>beforeIdParams（若非 null，如逻辑删除 UPDATE 的 deletedValue）</li>
     *   <li>主键值</li>
     *   <li>afterIdParams（若非 null，如逻辑删除 SELECT_BY_ID 的 normalValue）</li>
     * </ol>
     */
    public Object[] bindId(Object idValue) {
        if (beforeIdParams == null && afterIdParams == null) {
            return new Object[]{idValue};
        }
        List<Object> params = new ArrayList<>();
        if (beforeIdParams != null) {
            for (Object p : beforeIdParams) {
                params.add(p);
            }
        }
        params.add(idValue);
        if (afterIdParams != null) {
            for (Object p : afterIdParams) {
                params.add(p);
            }
        }
        return params.toArray();
    }

    /**
     * 返回 SELECT_ALL 模板的静态参数（逻辑删除过滤 normalValue），
     * 无逻辑删除字段时返回空数组。
     */
    public Object[] getStaticParams() {
        return afterIdParams != null ? afterIdParams.clone() : new Object[0];
    }

    /**
     * 根据字段类型初始化版本字段的零值（Long→0L，Integer→0）。
     */
    private static Object initVersionZero(FieldMeta field) {
        Class<?> type = field.getField().getType();
        if (type == Long.class || type == long.class) {
            return 0L;
        }
        return 0;
    }
}
