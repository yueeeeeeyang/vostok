package yueyang.vostok.query;

/**
 * 聚合字段描述。
 */
public class VKAggregate {
    private final VKAggregateType type;
    private final String field;
    private final String alias;

    private VKAggregate(VKAggregateType type, String field, String alias) {
        this.type = type;
        this.field = field;
        this.alias = alias;
    }

    
    public static VKAggregate count(String field, String alias) {
        return new VKAggregate(VKAggregateType.COUNT, field, alias);
    }

    
    public static VKAggregate countAll(String alias) {
        return new VKAggregate(VKAggregateType.COUNT, null, alias);
    }

    
    public static VKAggregate sum(String field, String alias) {
        return new VKAggregate(VKAggregateType.SUM, field, alias);
    }

    
    public static VKAggregate avg(String field, String alias) {
        return new VKAggregate(VKAggregateType.AVG, field, alias);
    }

    
    public static VKAggregate min(String field, String alias) {
        return new VKAggregate(VKAggregateType.MIN, field, alias);
    }

    
    public static VKAggregate max(String field, String alias) {
        return new VKAggregate(VKAggregateType.MAX, field, alias);
    }

    
    public VKAggregateType getType() {
        return type;
    }

    
    public String getField() {
        return field;
    }

    
    public String getAlias() {
        return alias;
    }
}
