package yueyang.vostok.data.query;

public class VKOrder {
    private final String field;
    private final boolean asc;

    private VKOrder(String field, boolean asc) {
        this.field = field;
        this.asc = asc;
    }

    public static VKOrder asc(String field) {
        return new VKOrder(field, true);
    }

    public static VKOrder desc(String field) {
        return new VKOrder(field, false);
    }

    public String getField() {
        return field;
    }

    public boolean isAsc() {
        return asc;
    }
}
