package yueyang.vostok.office.job;

/** 任务终态结果。 */
public final class VKOfficeJobResult {
    private final String jobId;
    private final VKOfficeJobStatus status;
    private final String resultPath;
    private final String errorCode;
    private final String errorMessage;

    public VKOfficeJobResult(String jobId,
                             VKOfficeJobStatus status,
                             String resultPath,
                             String errorCode,
                             String errorMessage) {
        this.jobId = jobId;
        this.status = status;
        this.resultPath = resultPath;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String jobId() {
        return jobId;
    }

    public VKOfficeJobStatus status() {
        return status;
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
}
