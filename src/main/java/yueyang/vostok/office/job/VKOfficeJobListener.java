package yueyang.vostok.office.job;

/** 任务通知监听器。 */
@FunctionalInterface
public interface VKOfficeJobListener {
    void onJob(VKOfficeJobNotification notification);
}
