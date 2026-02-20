package yueyang.vostok.data.sql;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.core.VKFieldCrypto;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.util.VKAssert;

import java.util.ArrayList;
import java.util.List;

public class SqlTemplate {
    private final String sql;
    private final List<FieldMeta> fields;
    private final FieldMeta idField;
    private final boolean appendId;
    private final boolean idOnly;

    public SqlTemplate(String sql, List<FieldMeta> fields, FieldMeta idField, boolean appendId, boolean idOnly) {
        this.sql = sql;
        this.fields = fields;
        this.idField = idField;
        this.appendId = appendId;
        this.idOnly = idOnly;
    }

    public String getSql() {
        return sql;
    }

    public Object[] bindEntity(Object entity, VKDataConfig config) {
        VKAssert.notNull(entity, "Entity is null");
        if (idOnly) {
            throw new IllegalStateException("Template is id-only");
        }
        List<Object> params = new ArrayList<>();
        for (FieldMeta field : fields) {
            Object raw = field.getValue(entity);
            params.add(VKFieldCrypto.encryptWrite(field, raw, config));
        }
        if (appendId && idField != null) {
            params.add(idField.getValue(entity));
        }
        return params.toArray();
    }

    public Object[] bindId(Object idValue) {
        return new Object[]{idValue};
    }
}
