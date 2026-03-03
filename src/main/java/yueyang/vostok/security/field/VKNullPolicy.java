package yueyang.vostok.security.field;

/**
 * 字段加密空值策略。
 *
 * <ul>
 *   <li>{@link #NULL_PASSTHROUGH}（默认）：null 输入直接返回 null，不加密也不报错。</li>
 *   <li>{@link #REJECT}：null 输入抛出 {@link yueyang.vostok.security.exception.VKSecurityException}。</li>
 * </ul>
 */
public enum VKNullPolicy {

    /** null 输入直接透传返回 null（默认） */
    NULL_PASSTHROUGH,

    /** null 输入抛出 VKSecurityException，拒绝写入 */
    REJECT
}
