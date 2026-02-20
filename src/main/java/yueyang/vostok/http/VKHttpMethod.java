package yueyang.vostok.http;

public enum VKHttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS;

    public static String normalize(String method) {
        if (method == null || method.isBlank()) {
            return GET.name();
        }
        return method.trim().toUpperCase();
    }
}
