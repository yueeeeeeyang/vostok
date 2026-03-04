package yueyang.vostok.office.job;

import java.util.List;

/** 任务存储接口。 */
public interface VKOfficeJobStore {
    void save(VKOfficeJobInfo info);

    void update(VKOfficeJobInfo info);

    VKOfficeJobInfo get(String jobId);

    List<VKOfficeJobInfo> list(VKOfficeJobQuery query);

    void removeFinishedBefore(long deadlineMillis);

    void clear();
}
