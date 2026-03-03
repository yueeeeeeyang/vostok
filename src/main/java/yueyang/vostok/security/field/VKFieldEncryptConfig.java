package yueyang.vostok.security.field;

/**
 * 数据库字段级加密配置。
 *
 * <p>配置项：
 * <ul>
 *   <li>{@link #dekCacheTtlSeconds}：DEK 缓存 TTL（秒）。{@code 0} = 禁用缓存（每次访问从 keyStore 加载），
 *       正数 = TTL 秒数。默认 300 秒。</li>
 *   <li>{@link #blindKeyIdSuffix}：Blind Key 文件名后缀，默认 {@code ".blind"}。
 *       自定义后缀可用于隔离不同业务的 Blind Key。</li>
 *   <li>{@link #nullPolicy}：空值处理策略，默认 {@link VKNullPolicy#NULL_PASSTHROUGH}。</li>
 * </ul>
 *
 * <p>使用 fluent setter 链式构建，{@link #copy()} 用于不可变配置传递。
 */
public class VKFieldEncryptConfig {

    /** DEK 缓存 TTL 秒数；0 = 禁用缓存 */
    private int dekCacheTtlSeconds = 300;

    /** Blind Key 文件名后缀（拼接到 columnKeyId 后形成文件名） */
    private String blindKeyIdSuffix = ".blind";

    /** 空值策略 */
    private VKNullPolicy nullPolicy = VKNullPolicy.NULL_PASSTHROUGH;

    public VKFieldEncryptConfig() {
    }

    /**
     * 设置 DEK 缓存 TTL 秒数。
     *
     * @param ttlSeconds 缓存 TTL 秒数；{@code 0} = 禁用缓存
     * @return this（支持链式调用）
     */
    public VKFieldEncryptConfig dekCacheTtlSeconds(int ttlSeconds) {
        this.dekCacheTtlSeconds = ttlSeconds;
        return this;
    }

    /**
     * 设置 Blind Key 文件名后缀。
     *
     * @param suffix 后缀字符串，不能为 null
     * @return this
     */
    public VKFieldEncryptConfig blindKeyIdSuffix(String suffix) {
        if (suffix != null) {
            this.blindKeyIdSuffix = suffix;
        }
        return this;
    }

    /**
     * 设置空值处理策略。
     *
     * @param policy 空值策略
     * @return this
     */
    public VKFieldEncryptConfig nullPolicy(VKNullPolicy policy) {
        if (policy != null) {
            this.nullPolicy = policy;
        }
        return this;
    }

    public int getDekCacheTtlSeconds() {
        return dekCacheTtlSeconds;
    }

    public String getBlindKeyIdSuffix() {
        return blindKeyIdSuffix;
    }

    public VKNullPolicy getNullPolicy() {
        return nullPolicy;
    }

    /**
     * 返回当前配置的深拷贝。
     */
    public VKFieldEncryptConfig copy() {
        VKFieldEncryptConfig c = new VKFieldEncryptConfig();
        c.dekCacheTtlSeconds = this.dekCacheTtlSeconds;
        c.blindKeyIdSuffix = this.blindKeyIdSuffix;
        c.nullPolicy = this.nullPolicy;
        return c;
    }
}
