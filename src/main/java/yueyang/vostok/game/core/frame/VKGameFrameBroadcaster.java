package yueyang.vostok.game.core.frame;

import yueyang.vostok.game.frame.VKGameFrame;
import yueyang.vostok.game.frame.VKGameFrameNotifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 帧同步独立广播器。
 *
 * <p>职责：
 * <ol>
 *   <li>管理每个帧同步房间的 {@link VKGameFrameNotifier}（房间级，一个房间一个回调），
 *       在每帧结束后直接触发回调，<b>完全绕过消息中心</b>，避免业务消息队列对帧数据的干扰。</li>
 *   <li>为每个帧同步房间维护一个环形缓冲区（Ring Buffer），保存最近 N 帧历史数据，
 *       供断线重连的客户端通过 {@link #pollFrames} 追赶进度。</li>
 * </ol>
 *
 * <p><b>架构隔离：</b>
 * {@code VKGameFrameBroadcaster} 与 {@link yueyang.vostok.game.core.message.VKGameMessageCenter}
 * 完全独立，各自维护独立的 notifier 和数据结构，互不干扰：
 * <ul>
 *   <li>消息中心负责有状态的业务消息（聊天、系统通知），提供排队、重试、ACK、TTL。</li>
 *   <li>帧广播器负责无状态的实时帧数据，直接回调、零排队、无重试，追求最低延迟。</li>
 * </ul>
 *
 * <p><b>线程模型：</b>
 * <ul>
 *   <li>{@link #broadcastFrame} 仅由 Tick Worker 线程调用（每个房间同一时刻只有一个 worker 处理），
 *       对同一房间的帧历史写入是单线程安全的。</li>
 *   <li>{@link #pollFrames} 可由任意 API 线程调用，使用 {@link AtomicReferenceArray}
 *       保证跨线程可见性（无锁读）。</li>
 *   <li>{@link #bindNotifier}/{@link #unbindNotifier} 可由任意线程调用，
 *       底层 ConcurrentHashMap 保证并发安全。</li>
 * </ul>
 */
public final class VKGameFrameBroadcaster {

    /**
     * 环形帧历史缓冲区，每个帧同步房间独享一个实例。
     *
     * <p>写入端（Tick 线程）：{@link #addFrame} 将帧写入 ring[frameNo % capacity]，
     * 再以 volatile 写更新 latestFrameNo，保证对读端可见（Java happens-before 规则）。
     *
     * <p>读取端（API 线程）：先读 volatile latestFrameNo，再按帧序号读 ring 中的数据；
     * 由于 volatile 读在 volatile 写之后，ring 中的有效帧对读端一定可见。
     */
    private static final class FrameHistory {
        // 使用 AtomicReferenceArray 保证数组元素的跨线程可见性（无锁），
        // 解决 plain array element 访问在 JMM 下可能的可见性问题。
        private final AtomicReferenceArray<VKGameFrame> ring;
        private final int capacity;
        // 最新已写入的帧序号；-1 表示尚未写入任何帧。
        // volatile 写（addFrame 末尾）作为 happens-before 屏障，
        // 保证 ring 写入对读端可见。
        private volatile long latestFrameNo = -1L;

        FrameHistory(int capacity) {
            this.capacity = Math.max(1, capacity);
            this.ring = new AtomicReferenceArray<>(this.capacity);
        }

        /**
         * 写入帧（Tick 线程调用，单写）。
         * ring 写入必须先于 latestFrameNo 的 volatile 写，以建立 happens-before。
         */
        void addFrame(VKGameFrame frame) {
            int idx = (int)(frame.frameNo() % capacity);
            ring.set(idx, frame);          // AtomicReferenceArray.set 是 volatile 写
            latestFrameNo = frame.frameNo(); // volatile 写，通知读端最新可见帧序号
        }

        /**
         * 拉取指定起始帧序号之后的历史帧（API 线程调用，多读）。
         *
         * @param fromFrameNo 期望的起始帧序号（含）
         * @param limit       最多返回帧数
         * @return 已有效存储的帧列表（可能少于 limit，也可能为空）
         */
        List<VKGameFrame> pollFrames(long fromFrameNo, int limit) {
            long latest = latestFrameNo; // volatile 读，建立 happens-before
            if (latest < 0 || fromFrameNo > latest) {
                return List.of();
            }
            // 环形缓冲区只保留最近 capacity 帧，超出范围的已被覆盖
            long bufferStart = Math.max(0L, latest - capacity + 1L);
            long start = Math.max(fromFrameNo, bufferStart);
            int maxLimit = Math.max(1, limit);

            List<VKGameFrame> result = new ArrayList<>(Math.min(maxLimit, (int)(latest - start + 1L)));
            for (long frameNo = start; frameNo <= latest && result.size() < maxLimit; frameNo++) {
                int idx = (int)(frameNo % capacity);
                VKGameFrame f = ring.get(idx); // volatile 读（通过 AtomicReferenceArray.get）
                // 双重校验：ring 中可能存放同模但不同帧号的旧帧（写入后被同 idx 新帧覆盖前瞬间）
                if (f != null && f.frameNo() == frameNo) {
                    result.add(f);
                }
            }
            return result;
        }
    }

    // 房间 ID → notifier（ConcurrentHashMap 保证并发安全）
    private final ConcurrentHashMap<String, VKGameFrameNotifier> notifiers = new ConcurrentHashMap<>();
    // 房间 ID → 帧历史（按需创建，房间关闭时清理）
    private final ConcurrentHashMap<String, FrameHistory> histories = new ConcurrentHashMap<>();

    /**
     * 绑定房间帧广播回调（线程安全）。
     *
     * @param roomId   房间 ID
     * @param notifier 帧回调，每帧 onTick 完成后触发；传 null 等效于解绑
     */
    public void bindNotifier(String roomId, VKGameFrameNotifier notifier) {
        if (notifier == null) {
            notifiers.remove(roomId);
        } else {
            notifiers.put(roomId, notifier);
        }
    }

    /**
     * 解绑房间帧广播回调（线程安全）。
     */
    public void unbindNotifier(String roomId) {
        notifiers.remove(roomId);
    }

    /**
     * 广播一帧数据（Tick Worker 线程调用）。
     *
     * <p>流程：
     * <ol>
     *   <li>将帧写入该房间的环形历史缓冲区（供断线客户端追帧）。</li>
     *   <li>若该房间绑定了 notifier，同步触发回调。
     *       回调中的异常由调用方（Runtime）捕获并记录，不影响 Tick 流程。</li>
     * </ol>
     *
     * @param frame 本帧数据
     * @param historyCapacity 环形缓冲区容量（取自当前配置，第一次写时初始化）
     */
    public void broadcastFrame(VKGameFrame frame, int historyCapacity) {
        // 写入历史缓冲区：computeIfAbsent 仅在首次创建时有短暂竞争，之后直接取缓存
        FrameHistory history = histories.computeIfAbsent(
                frame.roomId(),
                id -> new FrameHistory(historyCapacity)
        );
        history.addFrame(frame);

        // 直接触发回调，绕过消息中心，零排队
        VKGameFrameNotifier notifier = notifiers.get(frame.roomId());
        if (notifier != null) {
            notifier.onFrame(frame);
        }
    }

    /**
     * 拉取房间历史帧（供断线重连或慢客户端追帧，API 线程调用）。
     *
     * @param roomId      房间 ID
     * @param fromFrameNo 期望的起始帧序号（含）
     * @param limit       最多返回帧数
     * @return 帧列表，若房间不存在或尚无历史则返回空列表
     */
    public List<VKGameFrame> pollFrames(String roomId, long fromFrameNo, int limit) {
        FrameHistory history = histories.get(roomId);
        if (history == null) {
            return List.of();
        }
        return history.pollFrames(fromFrameNo, Math.max(1, limit));
    }

    /**
     * 房间关闭时清理该房间的 notifier 与历史缓冲区（由 Runtime 在房间关闭回调中调用）。
     */
    public void onRoomClosed(String roomId) {
        notifiers.remove(roomId);
        histories.remove(roomId);
    }

    /**
     * 运行时关闭时清空全部状态。
     */
    public void clear() {
        notifiers.clear();
        histories.clear();
    }
}
