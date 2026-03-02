package yueyang.vostok.config.listener;

import java.util.Map;
import java.util.Set;

/**
 * 配置变更事件，在热更新或 runtimeOverride 变更导致快照实质发生变化后触发。
 * <p>
 * changedKeys 包含所有发生变化的 key（新增、修改、删除），
 * 通过 oldValue/newValue 可获取变更前后的值，null 表示该 key 在对应快照中不存在。
 */
public final class VKConfigChangeEvent {

    private final Set<String> changedKeys;
    private final Map<String, String> oldData;
    private final Map<String, String> newData;

    public VKConfigChangeEvent(Set<String> changedKeys,
                               Map<String, String> oldData,
                               Map<String, String> newData) {
        this.changedKeys = Set.copyOf(changedKeys);
        this.oldData = oldData;
        this.newData = newData;
    }

    /** 本次变更涉及的所有 key（新增、修改、删除）。 */
    public Set<String> changedKeys() {
        return changedKeys;
    }

    /**
     * 指定 key 变更前的值。
     *
     * @return 变更前的值；若该 key 是本次新增的，返回 null
     */
    public String oldValue(String key) {
        return oldData.get(key);
    }

    /**
     * 指定 key 变更后的值。
     *
     * @return 变更后的值；若该 key 是本次删除的，返回 null
     */
    public String newValue(String key) {
        return newData.get(key);
    }

    /** 变更前的完整配置快照（只读）。 */
    public Map<String, String> oldSnapshot() {
        return oldData;
    }

    /** 变更后的完整配置快照（只读）。 */
    public Map<String, String> newSnapshot() {
        return newData;
    }
}
