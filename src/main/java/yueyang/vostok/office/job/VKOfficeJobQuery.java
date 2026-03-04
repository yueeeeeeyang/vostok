package yueyang.vostok.office.job;

/** 任务列表查询条件。 */
public final class VKOfficeJobQuery {
    private VKOfficeJobStatus status;
    private VKOfficeJobType type;
    private String tag;
    private int limit = 100;

    public static VKOfficeJobQuery create() {
        return new VKOfficeJobQuery();
    }

    public VKOfficeJobStatus status() {
        return status;
    }

    public VKOfficeJobQuery status(VKOfficeJobStatus status) {
        this.status = status;
        return this;
    }

    public VKOfficeJobType type() {
        return type;
    }

    public VKOfficeJobQuery type(VKOfficeJobType type) {
        this.type = type;
        return this;
    }

    public String tag() {
        return tag;
    }

    public VKOfficeJobQuery tag(String tag) {
        this.tag = tag;
        return this;
    }

    public int limit() {
        return limit;
    }

    public VKOfficeJobQuery limit(int limit) {
        this.limit = limit;
        return this;
    }
}
