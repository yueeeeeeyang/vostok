package yueyang.vostok.http;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VKHttpSseEvent {
    private final String event;
    private final String id;
    private final Long retryMs;
    private final String data;
    private final Map<String, String> extFields;

    public VKHttpSseEvent(String event, String id, Long retryMs, String data, Map<String, String> extFields) {
        this.event = event;
        this.id = id;
        this.retryMs = retryMs;
        this.data = data;
        this.extFields = extFields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(extFields));
    }

    public String getEvent() {
        return event;
    }

    public String getId() {
        return id;
    }

    public Long getRetryMs() {
        return retryMs;
    }

    public String getData() {
        return data;
    }

    public Map<String, String> getExtFields() {
        return extFields;
    }
}
