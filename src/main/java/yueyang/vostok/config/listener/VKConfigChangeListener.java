package yueyang.vostok.config.listener;

/**
 * 配置变更监听器。
 * <p>
 * 通过 {@code VostokConfig.addChangeListener} 注册后，每当配置快照发生实质变化
 * （热更新文件或 runtimeOverride 变更）时，该监听器会在持有锁的线程中同步回调。
 * 实现类不应阻塞或抛出异常，异常会被捕获并忽略，不影响配置主流程。
 */
@FunctionalInterface
public interface VKConfigChangeListener {

    /**
     * 配置发生变更时的回调。
     *
     * @param event 变更事件，包含变更 key 集合及新旧快照
     */
    void onChange(VKConfigChangeEvent event);
}
