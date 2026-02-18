package yueyang.vostok.web.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class VKHashedWheelTimer<T> {
    private final long tickMs;
    private final List<ArrayDeque<TimerEntry<T>>> wheel;
    private final AtomicLong tokenSeq = new AtomicLong(1);
    private long baseTimeMs;
    private long currentTick;

    VKHashedWheelTimer(long tickMs, int slots, long nowMs) {
        this.tickMs = Math.max(100, tickMs);
        int size = normalizeSlots(Math.max(16, slots));
        this.wheel = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            wheel.add(new ArrayDeque<>());
        }
        this.baseTimeMs = nowMs;
        this.currentTick = 0;
    }

    long schedule(T target, long deadlineMs) {
        long token = tokenSeq.getAndIncrement();
        add(new TimerEntry<>(target, token, deadlineMs));
        return token;
    }

    void pollExpired(long nowMs, Consumer<TimerEntry<T>> expiredConsumer) {
        long endTick = tickOf(nowMs);
        while (currentTick <= endTick) {
            int slot = (int) (currentTick & (wheel.size() - 1));
            ArrayDeque<TimerEntry<T>> bucket = wheel.get(slot);
            int round = bucket.size();
            for (int i = 0; i < round; i++) {
                TimerEntry<T> entry = bucket.pollFirst();
                if (entry == null) {
                    break;
                }
                if (entry.deadlineMs <= nowMs) {
                    expiredConsumer.accept(entry);
                } else {
                    add(entry);
                }
            }
            currentTick++;
        }
    }

    private void add(TimerEntry<T> entry) {
        long tick = tickOf(entry.deadlineMs);
        int slot = (int) (tick & (wheel.size() - 1));
        wheel.get(slot).addLast(entry);
    }

    private long tickOf(long timeMs) {
        if (timeMs <= baseTimeMs) {
            return 0;
        }
        return (timeMs - baseTimeMs) / tickMs;
    }

    private int normalizeSlots(int s) {
        int n = 1;
        while (n < s) {
            n <<= 1;
        }
        return n;
    }

    static final class TimerEntry<T> {
        final T target;
        final long token;
        final long deadlineMs;

        TimerEntry(T target, long token, long deadlineMs) {
            this.target = target;
            this.token = token;
            this.deadlineMs = deadlineMs;
        }
    }
}
