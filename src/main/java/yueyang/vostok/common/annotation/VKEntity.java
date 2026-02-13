package yueyang.vostok.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体类，由 Vostok 管理。
 * table 必填，需与数据库表名一致。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface VKEntity {
    /**
     * 数据库表名。
     */
    String table();

    /**
     * Web API 路径前缀（可选）。
     * 为空时使用默认规则从类名推导。
     */
    String path() default "";
}
