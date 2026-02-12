package yueyang.vostok.jdbc;

import java.util.List;

public class VKBatchResult {
    private final int[] counts;
    private final List<Object> keys;

    public VKBatchResult(int[] counts, List<Object> keys) {
        this.counts = counts;
        this.keys = keys;
    }

    public int[] getCounts() {
        return counts;
    }

    public List<Object> getKeys() {
        return keys;
    }

    public int total() {
        int sum = 0;
        if (counts != null) {
            for (int c : counts) {
                if (c > 0) {
                    sum += c;
                }
            }
        }
        return sum;
    }
}
