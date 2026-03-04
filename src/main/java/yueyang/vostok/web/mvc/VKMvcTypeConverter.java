package yueyang.vostok.web.mvc;

/** 字符串到目标类型的转换器。 */
@FunctionalInterface
public interface VKMvcTypeConverter<T> {
    T convert(String value, Class<T> targetType);
}
