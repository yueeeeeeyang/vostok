package yueyang.vostok.data.meta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class FieldMeta {
    private final Field field;
    private final String columnName;
    private final boolean id;
    private final boolean auto;
    private final MethodHandle getter;
    private final MethodHandle setter;

    public FieldMeta(Field field, String columnName, boolean id, boolean auto) {
        this.field = field;
        this.columnName = columnName;
        this.id = id;
        this.auto = auto;
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

    public boolean isId() {
        return id;
    }

    public boolean isAuto() {
        return auto;
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
