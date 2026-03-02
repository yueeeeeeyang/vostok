package yueyang.vostok.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 覆盖字段与列名的映射关系，并控制字段的持久化行为。
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

    /**
     * 是否将该字段包含在 INSERT 语句中，默认 {@code true}。
     * 设为 {@code false} 时 INSERT 不写入此列，列值由数据库默认值或触发器决定。
     */
    boolean insertable() default true;

    /**
     * 是否将该字段包含在 UPDATE SET 子句中，默认 {@code true}。
     * 设为 {@code false} 时 UPDATE 不修改此列，适用于创建时间等只写一次的字段。
     */
    boolean updatable() default true;

    /**
     * 列是否允许为空，默认 {@code true}。
     * 设为 {@code false} 时自动建表会为该列生成 {@code NOT NULL} 约束。
     */
    boolean nullable() default true;

    /**
     * 字符串类型列的长度，默认 {@code 255}。
     * 自动建表时对 {@code String} 和枚举类型生成 {@code VARCHAR(length)}。
     * 对非字符串类型（INT、BIGINT 等）无效。
     */
    int length() default 255;

    /**
     * 列是否添加唯一约束，默认 {@code false}。
     * 设为 {@code true} 时自动建表会为该列生成 {@code UNIQUE} 约束。
     */
    boolean unique() default false;
}
