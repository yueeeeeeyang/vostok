package yueyang.vostok.cache.pipeline;

import java.util.Collections;
import java.util.List;

/**
 * Pipeline 批量执行结果封装。
 * <p>
 * 保存每条命令的执行返回值（顺序与命令追加顺序一一对应）：
 * <ul>
 *   <li>SET / DEL / EXPIRE → null（无有意义返回值）</li>
 *   <li>INCRBY → {@link Long}，表示操作后的计数值</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * VKCachePipelineResult r = VostokCache.pipelineWithResult(pipe -> pipe.incrBy("cnt", 1));
 * long n = r.getCount(0); // 第 0 条命令（INCRBY）的结果
 * }</pre>
 */
public final class VKCachePipelineResult {
    private final List<Object> results;

    public VKCachePipelineResult(List<Object> results) {
        this.results = results == null ? List.of() : Collections.unmodifiableList(results);
    }

    /**
     * 获取第 {@code index} 条命令的原始结果对象。
     *
     * @param index 命令在 pipeline 中的位置（从 0 开始）
     * @return 返回值，可能为 null
     * @throws IndexOutOfBoundsException index 越界时抛出
     */
    public Object get(int index) {
        return results.get(index);
    }

    /**
     * 获取第 {@code index} 条命令（INCRBY 类型）的 Long 结果。
     *
     * @param index 命令索引
     * @return 计数器当前值；若命令类型不匹配则返回 0
     */
    public long getCount(int index) {
        Object v = results.get(index);
        return v instanceof Long l ? l : 0L;
    }

    /**
     * 返回 pipeline 中命令的总数。
     */
    public int size() {
        return results.size();
    }

    /**
     * 返回不可修改的原始结果列表。
     */
    public List<Object> results() {
        return results;
    }
}
