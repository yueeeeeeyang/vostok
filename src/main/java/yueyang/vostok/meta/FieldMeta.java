package yueyang.vostok.meta;

import java.lang.reflect.Field;

public class FieldMeta {
    private final Field field;
    private final String columnName;
    private final boolean id;
    private final boolean auto;

    public FieldMeta(Field field, String columnName, boolean id, boolean auto) {
        this.field = field;
        this.columnName = columnName;
        this.id = id;
        this.auto = auto;
        this.field.setAccessible(true);
    }

    public Field getField() {
        return field;
    }

    public String getColumnName() {
        return columnName;
    }

    public boolean isId() {
        return id;
    }

    public boolean isAuto() {
        return auto;
    }

    public Object getValue(Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read field: " + field.getName(), e);
        }
    }

    public void setValue(Object obj, Object value) {
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to set field: " + field.getName(), e);
        }
    }
}
