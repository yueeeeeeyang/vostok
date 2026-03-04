package yueyang.vostok.office.job;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 任务状态变更通知。 */
public final class VKOfficeJobNotification {
    private final String jobId;
    private final VKOfficeJobType type;
    private final String tag;
    private final VKOfficeJobStatus status;
    private final long submittedAt;
    private final long startedAt;
    private final long finishedAt;
    private final long durationMs;
    private final String resultPath;
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, Object> metrics;

    public VKOfficeJobNotification(String jobId,
                                   VKOfficeJobType type,
                                   String tag,
                                   VKOfficeJobStatus status,
                                   long submittedAt,
                                   long startedAt,
                                   long finishedAt,
                                   long durationMs,
                                   String resultPath,
                                   String errorCode,
                                   String errorMessage,
                                   Map<String, Object> metrics) {
        this.jobId = jobId;
        this.type = type;
        this.tag = tag;
        this.status = status;
        this.submittedAt = submittedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMs = durationMs;
        this.resultPath = resultPath;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        Map<String, Object> safe = metrics == null ? Map.of() : new LinkedHashMap<>(metrics);
        this.metrics = Collections.unmodifiableMap(safe);
    }

    public String jobId() {
        return jobId;
    }

    public VKOfficeJobType type() {
        return type;
    }

    public String tag() {
        return tag;
    }

    public VKOfficeJobStatus status() {
        return status;
    }

    public long submittedAt() {
        return submittedAt;
    }

    public long startedAt() {
        return startedAt;
    }

    public long finishedAt() {
        return finishedAt;
    }

    public long durationMs() {
        return durationMs;
    }

    public String resultPath() {
        return resultPath;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Map<String, Object> metrics() {
        return metrics;
    }
}
