package yueyang.vostok.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体的主键字段。
 * auto=true 时，INSERT 不包含该字段，并尝试将生成的主键写回实体。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VKId {
    /**
     * 是否为数据库自增主键。
     */
    boolean auto() default true;
}
