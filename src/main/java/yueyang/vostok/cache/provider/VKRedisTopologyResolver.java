package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKRedisMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

final class VKRedisTopologyResolver {
    private final VKRedisMode mode;
    private final List<VKRedisEndpoint> endpoints;
    private final AtomicInteger rr = new AtomicInteger(0);
    private final Map<String, Long> downUntilMs = new ConcurrentHashMap<>();

    VKRedisTopologyResolver(VKRedisMode mode, List<VKRedisEndpoint> endpoints) {
        this.mode = mode;
        this.endpoints = endpoints;
    }

    VKRedisEndpoint choose(String keyHint) {
        List<VKRedisEndpoint> live = liveEndpoints();
        if (live.isEmpty()) {
            live = endpoints;
        }
        if (live.isEmpty()) {
            throw new IllegalStateException("No redis endpoints available");
        }
        if (mode == VKRedisMode.SINGLE || live.size() == 1) {
            return live.get(0);
        }
        if (mode == VKRedisMode.SENTINEL) {
            int idx = Math.floorMod(rr.getAndIncrement(), live.size());
            return live.get(idx);
        }
        long hash = crc32(keyHint == null ? "*" : keyHint);
        int idx = (int) (hash % live.size());
        return live.get(idx);
    }

    void markFailure(VKRedisEndpoint endpoint) {
        long backoff = ThreadLocalRandom.current().nextLong(1000, 3000);
        downUntilMs.put(endpoint.key(), System.currentTimeMillis() + backoff);
    }

    void markSuccess(VKRedisEndpoint endpoint) {
        downUntilMs.remove(endpoint.key());
    }

    List<VKRedisEndpoint> endpoints() {
        return new ArrayList<>(endpoints);
    }

    private List<VKRedisEndpoint> liveEndpoints() {
        long now = System.currentTimeMillis();
        List<VKRedisEndpoint> out = new ArrayList<>();
        for (VKRedisEndpoint endpoint : endpoints) {
            Long until = downUntilMs.get(endpoint.key());
            if (until == null || until <= now) {
                out.add(endpoint);
            }
        }
        out.sort(Comparator.comparing(VKRedisEndpoint::key));
        return out;
    }

    private long crc32(String text) {
        CRC32 crc = new CRC32();
        crc.update(text.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
