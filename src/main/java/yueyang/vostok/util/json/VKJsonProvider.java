package yueyang.vostok.util.json;

public interface VKJsonProvider {
    String name();

    String toJson(Object value);

    <T> T fromJson(String json, Class<T> type);
}
