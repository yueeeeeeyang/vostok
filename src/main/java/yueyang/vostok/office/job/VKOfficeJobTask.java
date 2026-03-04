package yueyang.vostok.office.job;

/** 任务执行函数。 */
@FunctionalInterface
public interface VKOfficeJobTask {
    VKOfficeJobExecutionResult execute() throws Exception;
}
