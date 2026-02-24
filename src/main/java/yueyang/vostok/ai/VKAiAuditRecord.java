package yueyang.vostok.ai;

public class VKAiAuditRecord {
    private final long timestampMs;
    private final String type;
    private final String clientName;
    private final String detail;

    public VKAiAuditRecord(long timestampMs, String type, String clientName, String detail) {
        this.timestampMs = timestampMs;
        this.type = type;
        this.clientName = clientName;
        this.detail = detail;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getType() {
        return type;
    }

    public String getClientName() {
        return clientName;
    }

    public String getDetail() {
        return detail;
    }
}
