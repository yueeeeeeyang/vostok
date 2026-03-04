package yueyang.vostok.office.job;

/**
 * 异步任务提交请求。
 *
 * <p>tag 可用于业务分组（如租户、批次）。</p>
 */
public final class VKOfficeJobRequest {
    private VKOfficeJobType type = VKOfficeJobType.CUSTOM;
    private String tag;
    private VKOfficeJobTask task;

    public static VKOfficeJobRequest create(VKOfficeJobTask task) {
        return new VKOfficeJobRequest().task(task);
    }

    public VKOfficeJobType type() {
        return type;
    }

    public VKOfficeJobRequest type(VKOfficeJobType type) {
        if (type != null) {
            this.type = type;
        }
        return this;
    }

    public String tag() {
        return tag;
    }

    public VKOfficeJobRequest tag(String tag) {
        this.tag = tag;
        return this;
    }

    public VKOfficeJobTask task() {
        return task;
    }

    public VKOfficeJobRequest task(VKOfficeJobTask task) {
        this.task = task;
        return this;
    }
}
