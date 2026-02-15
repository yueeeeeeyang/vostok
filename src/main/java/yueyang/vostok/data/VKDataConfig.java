package yueyang.vostok.data;

import yueyang.vostok.data.config.VKBatchFailStrategy;
import yueyang.vostok.data.dialect.VKDialectType;

/**
 * Vostok 数据源与连接池配置。
 * 只能通过 Vostok.Data.init(...) 显式传入。
 */
public class VKDataConfig {
    /** JDBC URL，例如 jdbc:mysql://host:3306/db */
    private String url;
    /** 数据库用户名 */
    private String username;
    /** 数据库密码 */
    private String password;
    /** JDBC 驱动类名 */
    private String driver;
    /** 方言类型（可选，不设置时自动推断） */
    private VKDialectType dialect;
    /** 是否校验 DDL */
    private boolean validateDdl = false;
    /** DDL 校验的 schema（可选） */
    private String ddlSchema;
    /** 初始化/刷新时是否自动创建缺失表 */
    private boolean autoCreateTable = false;
    /** 最小空闲连接数 */
    private int minIdle = 1;
    /** 最大活动连接数 */
    private int maxActive = 10;
    /** 获取连接最大等待时间（毫秒） */
    private long maxWaitMs = 30000;
    /** 是否在借出时校验连接有效性 */
    private boolean testOnBorrow = false;
    /** 是否在归还时校验连接有效性 */
    private boolean testOnReturn = false;
    /** 连接有效性检测 SQL（可选，优先于 isValid） */
    private String validationQuery;
    /** 连接有效性检测超时（秒） */
    private int validationTimeoutSec = 2;
    /** 空闲连接定期校验与回收间隔（毫秒，<=0 不启用） */
    private long idleValidationIntervalMs = 0;
    /** 是否启用连接预热 */
    private boolean preheatEnabled = true;
    /** 空闲超时（毫秒），<=0 表示不回收 */
    private long idleTimeoutMs = 0;
    /** 连接泄露检测阈值（毫秒），<=0 表示不检测 */
    private long leakDetectMs = 0;
    /** 预编译 SQL 缓存大小（每个连接） */
    private int statementCacheSize = 50;
    /** SQL 模板缓存大小（每个数据源） */
    private int sqlTemplateCacheSize = 200;
    /** 是否启用可重试异常处理 */
    private boolean retryEnabled = false;
    /** 最大重试次数 */
    private int maxRetries = 2;
    /** 指数退避基数（毫秒） */
    private long retryBackoffBaseMs = 50;
    /** 指数退避最大值（毫秒） */
    private long retryBackoffMaxMs = 2000;
    /** 可重试 SQLState 前缀白名单 */
    private String[] retrySqlStatePrefixes = new String[]{"08", "40", "57"};
    /** 批处理分片大小 */
    private int batchSize = 500;
    /** 批处理失败策略 */
    private VKBatchFailStrategy batchFailStrategy = VKBatchFailStrategy.FAIL_FAST;
    /** 是否打印 SQL 日志 */
    private boolean logSql = false;
    /** 是否打印 SQL 参数 */
    private boolean logParams = false;
    /** 慢 SQL 阈值（毫秒），<=0 不记录慢 SQL */
    private long slowSqlMs = 0;
    /** 是否启用 SQL 耗时分布统计 */
    private boolean sqlMetricsEnabled = true;
    /** 慢 SQL TopN 数量 */
    private int slowSqlTopN = 0;
    /** 是否启用 Savepoint 支持 */
    private boolean savepointEnabled = true;
    /** 事务超时（毫秒，<=0 不限制） */
    private long txTimeoutMs = 0;
    /** 非事务 SQL 超时（毫秒，<=0 不限制） */
    private long queryTimeoutMs = 0;

    
    public String getUrl() {
        return url;
    }

    
    public VKDataConfig url(String url) {
        this.url = url;
        return this;
    }

    
    public String getUsername() {
        return username;
    }

    
    public VKDataConfig username(String username) {
        this.username = username;
        return this;
    }

    
    public String getPassword() {
        return password;
    }

    
    public VKDataConfig password(String password) {
        this.password = password;
        return this;
    }

    
    public String getDriver() {
        return driver;
    }

    
    public VKDataConfig driver(String driver) {
        this.driver = driver;
        return this;
    }

    
    public VKDialectType getDialect() {
        return dialect;
    }

    
    public VKDataConfig dialect(VKDialectType dialect) {
        this.dialect = dialect;
        return this;
    }

    
    public boolean isValidateDdl() {
        return validateDdl;
    }

    
    public VKDataConfig validateDdl(boolean validateDdl) {
        this.validateDdl = validateDdl;
        return this;
    }

    public boolean isAutoCreateTable() {
        return autoCreateTable;
    }

    public VKDataConfig autoCreateTable(boolean autoCreateTable) {
        this.autoCreateTable = autoCreateTable;
        return this;
    }

    
    public String getDdlSchema() {
        return ddlSchema;
    }

    
    public VKDataConfig ddlSchema(String ddlSchema) {
        this.ddlSchema = ddlSchema;
        return this;
    }

    
    public int getMinIdle() {
        return minIdle;
    }

    
    public VKDataConfig minIdle(int minIdle) {
        this.minIdle = minIdle;
        return this;
    }

    
    public int getMaxActive() {
        return maxActive;
    }

    
    public VKDataConfig maxActive(int maxActive) {
        this.maxActive = maxActive;
        return this;
    }

    
    public long getMaxWaitMs() {
        return maxWaitMs;
    }

    
    public VKDataConfig maxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
        return this;
    }

    
    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    
    public VKDataConfig testOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        return this;
    }

    
    public boolean isTestOnReturn() {
        return testOnReturn;
    }

    
    public VKDataConfig testOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        return this;
    }

    
    public String getValidationQuery() {
        return validationQuery;
    }

    
    public VKDataConfig validationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
        return this;
    }

    
    public int getValidationTimeoutSec() {
        return validationTimeoutSec;
    }

    
    public VKDataConfig validationTimeoutSec(int validationTimeoutSec) {
        this.validationTimeoutSec = validationTimeoutSec;
        return this;
    }

    
    public long getIdleValidationIntervalMs() {
        return idleValidationIntervalMs;
    }

    
    public VKDataConfig idleValidationIntervalMs(long idleValidationIntervalMs) {
        this.idleValidationIntervalMs = idleValidationIntervalMs;
        return this;
    }

    
    public boolean isPreheatEnabled() {
        return preheatEnabled;
    }

    
    public VKDataConfig preheatEnabled(boolean preheatEnabled) {
        this.preheatEnabled = preheatEnabled;
        return this;
    }

    
    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    
    public VKDataConfig idleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
        return this;
    }

    
    public long getLeakDetectMs() {
        return leakDetectMs;
    }

    
    public VKDataConfig leakDetectMs(long leakDetectMs) {
        this.leakDetectMs = leakDetectMs;
        return this;
    }

    
    public int getStatementCacheSize() {
        return statementCacheSize;
    }

    
    public VKDataConfig statementCacheSize(int statementCacheSize) {
        this.statementCacheSize = statementCacheSize;
        return this;
    }

    
    public int getSqlTemplateCacheSize() {
        return sqlTemplateCacheSize;
    }

    
    public VKDataConfig sqlTemplateCacheSize(int sqlTemplateCacheSize) {
        this.sqlTemplateCacheSize = sqlTemplateCacheSize;
        return this;
    }

    
    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    
    public VKDataConfig retryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
        return this;
    }

    
    public int getMaxRetries() {
        return maxRetries;
    }

    
    public VKDataConfig maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    
    public long getRetryBackoffBaseMs() {
        return retryBackoffBaseMs;
    }

    
    public VKDataConfig retryBackoffBaseMs(long retryBackoffBaseMs) {
        this.retryBackoffBaseMs = retryBackoffBaseMs;
        return this;
    }

    
    public long getRetryBackoffMaxMs() {
        return retryBackoffMaxMs;
    }

    
    public VKDataConfig retryBackoffMaxMs(long retryBackoffMaxMs) {
        this.retryBackoffMaxMs = retryBackoffMaxMs;
        return this;
    }

    
    public String[] getRetrySqlStatePrefixes() {
        return retrySqlStatePrefixes;
    }

    
    public VKDataConfig retrySqlStatePrefixes(String... retrySqlStatePrefixes) {
        this.retrySqlStatePrefixes = retrySqlStatePrefixes;
        return this;
    }

    
    public int getBatchSize() {
        return batchSize;
    }

    
    public VKDataConfig batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    
    public VKBatchFailStrategy getBatchFailStrategy() {
        return batchFailStrategy;
    }

    
    public VKDataConfig batchFailStrategy(VKBatchFailStrategy batchFailStrategy) {
        this.batchFailStrategy = batchFailStrategy;
        return this;
    }

    
    public boolean isLogSql() {
        return logSql;
    }

    
    public VKDataConfig logSql(boolean logSql) {
        this.logSql = logSql;
        return this;
    }

    
    public boolean isLogParams() {
        return logParams;
    }

    
    public VKDataConfig logParams(boolean logParams) {
        this.logParams = logParams;
        return this;
    }

    
    public long getSlowSqlMs() {
        return slowSqlMs;
    }

    
    public VKDataConfig slowSqlMs(long slowSqlMs) {
        this.slowSqlMs = slowSqlMs;
        return this;
    }

    
    public boolean isSqlMetricsEnabled() {
        return sqlMetricsEnabled;
    }

    
    public VKDataConfig sqlMetricsEnabled(boolean sqlMetricsEnabled) {
        this.sqlMetricsEnabled = sqlMetricsEnabled;
        return this;
    }

    
    public int getSlowSqlTopN() {
        return slowSqlTopN;
    }

    
    public VKDataConfig slowSqlTopN(int slowSqlTopN) {
        this.slowSqlTopN = slowSqlTopN;
        return this;
    }

    
    public boolean isSavepointEnabled() {
        return savepointEnabled;
    }

    
    public VKDataConfig savepointEnabled(boolean savepointEnabled) {
        this.savepointEnabled = savepointEnabled;
        return this;
    }

    
    public long getTxTimeoutMs() {
        return txTimeoutMs;
    }

    
    public VKDataConfig txTimeoutMs(long txTimeoutMs) {
        this.txTimeoutMs = txTimeoutMs;
        return this;
    }

    
    public long getQueryTimeoutMs() {
        return queryTimeoutMs;
    }

    
    public VKDataConfig queryTimeoutMs(long queryTimeoutMs) {
        this.queryTimeoutMs = queryTimeoutMs;
        return this;
    }
}
