package yueyang.vostok.data.sql;

public class SqlAndParams {
    private final String sql;
    private final Object[] params;

    public SqlAndParams(String sql, Object[] params) {
        this.sql = sql;
        this.params = params;
    }

    public String getSql() {
        return sql;
    }

    public Object[] getParams() {
        return params;
    }
}
