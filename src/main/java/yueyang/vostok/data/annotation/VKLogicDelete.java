package yueyang.vostok.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记字段为逻辑删除标志，框架将物理删除转换为字段标记更新，并在查询中自动过滤已删除记录。
 *
 * <p>行为说明：
 * <ul>
 *   <li>INSERT：若字段值为 null，自动初始化为 {@link #normalValue()}。</li>
 *   <li>DELETE：不执行物理 {@code DELETE}，而是 {@code UPDATE table SET del_col = deletedValue WHERE id = ?}。</li>
 *   <li>SELECT（findById / findAll / query / count / aggregate）：
 *       所有查询自动追加 {@code AND del_col = normalValue} 过滤条件，对调用方透明。</li>
 *   <li>UPDATE：逻辑删除字段默认参与 UPDATE，可将记录从已删除状态恢复为正常状态。</li>
 * </ul>
 *
 * <p>字段类型支持：{@code String}、{@code Integer}/{@code int}、{@code Long}/{@code long}、
 * {@code Boolean}/{@code boolean}。{@link #deletedValue()} / {@link #normalValue()}
 * 将按字段实际类型自动转换。
 *
 * <p>每个实体类最多允许一个 {@code @VKLogicDelete} 字段。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VKLogicDelete {

    /**
     * 逻辑删除状态的标志值（已删除），默认 {@code "1"}。
     */
    String deletedValue() default "1";

    /**
     * 正常（未删除）状态的标志值，默认 {@code "0"}。
     */
    String normalValue() default "0";
}
