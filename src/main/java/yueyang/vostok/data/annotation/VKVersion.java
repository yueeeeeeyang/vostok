package yueyang.vostok.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记字段为乐观锁版本号字段，框架自动维护版本递增与冲突检测。
 *
 * <p>行为说明：
 * <ul>
 *   <li>INSERT：若字段值为 null，自动初始化为 0；否则以实体值为准。</li>
 *   <li>UPDATE：SET 子句生成 {@code version = version + 1}（数据库侧原子自增），
 *       WHERE 子句追加 {@code AND version = ?}（持有旧版本值），
 *       若更新影响行数为 0 则抛出 {@link yueyang.vostok.data.exception.VKOptimisticLockException}；
 *       更新成功后自动将实体中的版本字段自增。</li>
 *   <li>批量更新：同样在 WHERE 中带版本条件，但不抛异常，通过 {@code VKBatchItemResult.getCount()==0} 判断冲突。</li>
 * </ul>
 *
 * <p>字段类型要求：必须为 {@code Long}、{@code long}、{@code Integer} 或 {@code int}。
 *
 * <p>每个实体类最多允许一个 {@code @VKVersion} 字段。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VKVersion {
}
