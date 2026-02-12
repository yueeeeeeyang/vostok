package yueyang.vostok.jdbc;

import yueyang.vostok.config.DataSourceConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * SQL 耗时统计：分布 + 慢 SQL TopN。
 */
public class VKSqlMetrics {
    private static final long[] DEFAULT_BUCKETS = new long[]{1, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};

    private final boolean enabled;
    private final long[] buckets;
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong totalCost = new AtomicLong(0);
    private final AtomicLong maxCost = new AtomicLong(0);
    private final AtomicLongArray bucketCounts;
    private final int topN;
    private final long slowThresholdMs;
    private final boolean includeParams;
    private final PriorityQueue<SlowEntry> slowTopN;

    public VKSqlMetrics(DataSourceConfig config) {
        this.enabled = config.isSqlMetricsEnabled();
        this.buckets = Arrays.copyOf(DEFAULT_BUCKETS, DEFAULT_BUCKETS.length);
        this.bucketCounts = new AtomicLongArray(buckets.length + 1);
        this.topN = Math.max(0, config.getSlowSqlTopN());
        this.slowThresholdMs = config.getSlowSqlMs();
        this.includeParams = config.isLogParams();
        this.slowTopN = new PriorityQueue<>(Comparator.comparingLong(e -> e.costMs));
    }

    
    public void record(String sql, Object[] params, long costMs) {
        if (!enabled) {
            return;
        }
        totalCount.incrementAndGet();
        totalCost.addAndGet(costMs);
        updateMax(costMs);
        int idx = bucketIndex(costMs);
        bucketCounts.incrementAndGet(idx);
        if (topN <= 0) {
            return;
        }
        if (slowThresholdMs > 0 && costMs < slowThresholdMs) {
            return;
        }
        String paramText = includeParams && params != null ? Arrays.toString(params) : null;
        addSlow(new SlowEntry(sql, paramText, costMs, System.currentTimeMillis()));
    }

    
    private void updateMax(long costMs) {
        long prev;
        do {
            prev = maxCost.get();
            if (costMs <= prev) {
                return;
            }
        } while (!maxCost.compareAndSet(prev, costMs));
    }

    
    private int bucketIndex(long costMs) {
        for (int i = 0; i < buckets.length; i++) {
            if (costMs <= buckets[i]) {
                return i;
            }
        }
        return buckets.length;
    }

    
    private void addSlow(SlowEntry entry) {
        synchronized (slowTopN) {
            if (slowTopN.size() < topN) {
                slowTopN.offer(entry);
                return;
            }
            SlowEntry min = slowTopN.peek();
            if (min != null && entry.costMs > min.costMs) {
                slowTopN.poll();
                slowTopN.offer(entry);
            }
        }
    }

    
    public long getTotalCount() {
        return totalCount.get();
    }

    
    public long getTotalCost() {
        return totalCost.get();
    }

    
    public long getMaxCost() {
        return maxCost.get();
    }

    
    public long[] getBuckets() {
        return Arrays.copyOf(buckets, buckets.length);
    }

    
    public long[] getBucketCounts() {
        long[] counts = new long[bucketCounts.length()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = bucketCounts.get(i);
        }
        return counts;
    }

    
    public List<SlowEntry> getSlowTopN() {
        synchronized (slowTopN) {
            List<SlowEntry> list = new ArrayList<>(slowTopN);
            list.sort((a, b) -> Long.compare(b.costMs, a.costMs));
            return list;
        }
    }

    /**
     * 慢 SQL 记录。
     */
    public static class SlowEntry {
        private final String sql;
        private final String params;
        private final long costMs;
        private final long timeMs;

        public SlowEntry(String sql, String params, long costMs, long timeMs) {
            this.sql = sql;
            this.params = params;
            this.costMs = costMs;
            this.timeMs = timeMs;
        }

        
        public String getSql() {
            return sql;
        }

        
        public String getParams() {
            return params;
        }

        
        public long getCostMs() {
            return costMs;
        }

        
        public long getTimeMs() {
            return timeMs;
        }
    }
}
