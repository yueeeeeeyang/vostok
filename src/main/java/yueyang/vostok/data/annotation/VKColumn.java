package yueyang.vostok.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 覆盖字段与列名的映射关系。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VKColumn {
    /**
     * 数据库列名。
     */
    String name();

    /**
     * 是否对该字段进行加密存储。
     */
    boolean encrypted() default false;

    /**
     * 字段加密使用的 keyId；为空时走 VKDataConfig.defaultEncryptionKeyId。
     */
    String keyId() default "";
}
