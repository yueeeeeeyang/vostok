package yueyang.vostok.config.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在配置绑定 POJO 类上，声明该类对应的 config key 前缀。
 * <p>
 * 配合 {@code VostokConfig.bind(MyConfig.class)} 使用，无需在调用侧重复写前缀。
 *
 * <pre>{@code
 * @VKConfigPrefix("database")
 * public class DatabaseConfig {
 *     private String host;
 *     private int port;
 * }
 *
 * DatabaseConfig cfg = VostokConfig.bind(DatabaseConfig.class);
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface VKConfigPrefix {

    /** config key 前缀，不含尾部 '.'，例如 {@code "database"} 或 {@code "server.http"}。 */
    String value();
}
