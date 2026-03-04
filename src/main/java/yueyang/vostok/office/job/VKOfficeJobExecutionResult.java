package yueyang.vostok.office.job;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 任务执行函数返回值。 */
public final class VKOfficeJobExecutionResult {
    private final String resultPath;
    private final Map<String, Object> metrics;

    public VKOfficeJobExecutionResult(String resultPath, Map<String, Object> metrics) {
        this.resultPath = resultPath;
        Map<String, Object> safe = metrics == null ? Map.of() : new LinkedHashMap<>(metrics);
        this.metrics = Collections.unmodifiableMap(safe);
    }

    public static VKOfficeJobExecutionResult empty() {
        return new VKOfficeJobExecutionResult(null, Map.of());
    }

    public static VKOfficeJobExecutionResult ofPath(String resultPath) {
        return new VKOfficeJobExecutionResult(resultPath, Map.of());
    }

    public String resultPath() {
        return resultPath;
    }

    public Map<String, Object> metrics() {
        return metrics;
    }
}
