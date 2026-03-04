package yueyang.vostok.office.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 默认任务存储：内存实现。 */
public final class VKInMemoryOfficeJobStore implements VKOfficeJobStore {
    private final ConcurrentHashMap<String, VKOfficeJobInfo> data = new ConcurrentHashMap<>();

    @Override
    public void save(VKOfficeJobInfo info) {
        if (info == null || info.jobId() == null) {
            return;
        }
        data.put(info.jobId(), info);
    }

    @Override
    public void update(VKOfficeJobInfo info) {
        if (info == null || info.jobId() == null) {
            return;
        }
        data.put(info.jobId(), info);
    }

    @Override
    public VKOfficeJobInfo get(String jobId) {
        return jobId == null ? null : data.get(jobId);
    }

    @Override
    public List<VKOfficeJobInfo> list(VKOfficeJobQuery query) {
        VKOfficeJobQuery q = query == null ? VKOfficeJobQuery.create() : query;
        int max = q.limit() <= 0 ? 100 : q.limit();
        List<VKOfficeJobInfo> out = new ArrayList<>();
        for (VKOfficeJobInfo info : data.values()) {
            if (q.status() != null && info.status() != q.status()) {
                continue;
            }
            if (q.type() != null && info.type() != q.type()) {
                continue;
            }
            if (q.tag() != null && !q.tag().isBlank() && !Objects.equals(q.tag(), info.tag())) {
                continue;
            }
            out.add(info);
        }
        out.sort(Comparator.comparingLong(VKOfficeJobInfo::submittedAt).reversed());
        if (out.size() > max) {
            return out.subList(0, max);
        }
        return out;
    }

    @Override
    public void removeFinishedBefore(long deadlineMillis) {
        if (deadlineMillis <= 0) {
            return;
        }
        for (var entry : data.entrySet()) {
            VKOfficeJobInfo info = entry.getValue();
            if (info == null) {
                continue;
            }
            if (info.finishedAt() > 0 && info.finishedAt() < deadlineMillis) {
                data.remove(entry.getKey(), info);
            }
        }
    }

    @Override
    public void clear() {
        data.clear();
    }
}
