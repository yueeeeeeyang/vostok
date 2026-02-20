package yueyang.vostok.cache.provider;

record VKRedisEndpoint(String host, int port) {
    static VKRedisEndpoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Redis endpoint is blank");
        }
        String[] parts = raw.trim().split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Redis endpoint format invalid: " + raw);
        }
        return new VKRedisEndpoint(parts[0].trim(), Integer.parseInt(parts[1].trim()));
    }

    String key() {
        return host + ":" + port;
    }
}
