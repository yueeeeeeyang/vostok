package yueyang.vostok.query;

import java.util.Arrays;
import java.util.List;

public class VKCondition {
    private final String field;
    private final String rawExpr;
    private final VKOperator op;
    private final List<Object> values;
    private final String subquery;
    private final List<Object> subParams;

    private VKCondition(String field, String rawExpr, VKOperator op, List<Object> values, String subquery, List<Object> subParams) {
        this.field = field;
        this.rawExpr = rawExpr;
        this.op = op;
        this.values = values;
        this.subquery = subquery;
        this.subParams = subParams;
    }

    public static VKCondition of(String field, VKOperator op, Object... values) {
        return new VKCondition(field, null, op, values == null ? List.of() : Arrays.asList(values), null, List.of());
    }

    public static VKCondition raw(String expr, VKOperator op, Object... values) {
        return new VKCondition(null, expr, op, values == null ? List.of() : Arrays.asList(values), null, List.of());
    }

    public static VKCondition inSubquery(String field, String subquery, Object... params) {
        return new VKCondition(field, null, VKOperator.IN,
                List.of(), subquery, params == null ? List.of() : Arrays.asList(params));
    }

    public static VKCondition notInSubquery(String field, String subquery, Object... params) {
        return new VKCondition(field, null, VKOperator.NOT_IN,
                List.of(), subquery, params == null ? List.of() : Arrays.asList(params));
    }

    public static VKCondition exists(String subquery, Object... params) {
        return new VKCondition(null, null, VKOperator.EXISTS,
                List.of(), subquery, params == null ? List.of() : Arrays.asList(params));
    }

    public static VKCondition notExists(String subquery, Object... params) {
        return new VKCondition(null, null, VKOperator.NOT_EXISTS,
                List.of(), subquery, params == null ? List.of() : Arrays.asList(params));
    }

    public String getField() {
        return field;
    }

    public String getRawExpr() {
        return rawExpr;
    }

    public VKOperator getOp() {
        return op;
    }

    public List<Object> getValues() {
        return values;
    }

    public String getSubquery() {
        return subquery;
    }

    public List<Object> getSubParams() {
        return subParams;
    }
}
