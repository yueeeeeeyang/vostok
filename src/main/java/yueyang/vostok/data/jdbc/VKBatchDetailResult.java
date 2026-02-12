package yueyang.vostok.data.jdbc;

import java.util.Collections;
import java.util.List;

/**
 * 批处理详细结果（每条明细）。
 */
public class VKBatchDetailResult {
    private final List<VKBatchItemResult> items;

    public VKBatchDetailResult(List<VKBatchItemResult> items) {
        this.items = items;
    }

    
    public List<VKBatchItemResult> getItems() {
        return items == null ? List.of() : Collections.unmodifiableList(items);
    }

    
    public int totalSuccess() {
        int sum = 0;
        for (VKBatchItemResult item : getItems()) {
            if (item.isSuccess() && item.getCount() > 0) {
                sum += item.getCount();
            }
        }
        return sum;
    }

    
    public int totalFail() {
        int sum = 0;
        for (VKBatchItemResult item : getItems()) {
            if (!item.isSuccess()) {
                sum++;
            }
        }
        return sum;
    }
}
