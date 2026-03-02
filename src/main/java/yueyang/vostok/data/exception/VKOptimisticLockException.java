package yueyang.vostok.data.exception;

/**
 * 乐观锁冲突异常，当 UPDATE 操作因版本号不匹配而影响行数为 0 时抛出。
 *
 * <p>触发场景：实体字段标记了 {@code @VKVersion}，调用 {@code Vostok.Data.update(entity)} 时，
 * 若另一个事务已先行更新了同一记录（版本号已变化），则本次更新影响行数为 0，抛出此异常。
 *
 * <p>错误码：{@code DK-550}。
 *
 * <p>推荐处理方式：捕获此异常后重新查询最新实体，合并业务变更后再次尝试更新。
 */
public class VKOptimisticLockException extends VKException {

    /**
     * @param message 包含实体类名和版本信息的描述
     */
    public VKOptimisticLockException(String message) {
        super(VKErrorCode.OPTIMISTIC_LOCK, message);
    }

    public VKOptimisticLockException(String message, Throwable cause) {
        super(VKErrorCode.OPTIMISTIC_LOCK, message, cause);
    }
}
