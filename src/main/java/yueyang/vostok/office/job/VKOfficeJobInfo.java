package yueyang.vostok.office.job;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 任务状态快照。 */
public final class VKOfficeJobInfo {
    private final String jobId;
    private final VKOfficeJobType type;
    private final String tag;
    private final VKOfficeJobStatus status;
    private final long submittedAt;
    private final long startedAt;
    private final long finishedAt;
    private final String resultPath;
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, Object> metrics;

    public VKOfficeJobInfo(String jobId,
                           VKOfficeJobType type,
                           String tag,
                           VKOfficeJobStatus status,
                           long submittedAt,
                           long startedAt,
                           long finishedAt,
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

    public String resultPath() {
        return resultPath;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public long durationMs() {
        if (startedAt <= 0 || finishedAt <= 0) {
            return 0;
        }
        return Math.max(0, finishedAt - startedAt);
    }

    public Map<String, Object> metrics() {
        return metrics;
    }
}
