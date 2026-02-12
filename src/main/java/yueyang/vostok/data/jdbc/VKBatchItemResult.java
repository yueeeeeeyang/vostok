package yueyang.vostok.data.jdbc;

/**
 * 单条批处理结果。
 */
public class VKBatchItemResult {
    private final int index;
    private final boolean success;
    private final int count;
    private final Object key;
    private final String error;

    public VKBatchItemResult(int index, boolean success, int count, Object key, String error) {
        this.index = index;
        this.success = success;
        this.count = count;
        this.key = key;
        this.error = error;
    }

    
    public int getIndex() {
        return index;
    }

    
    public boolean isSuccess() {
        return success;
    }

    
    public int getCount() {
        return count;
    }

    
    public Object getKey() {
        return key;
    }

    
    public String getError() {
        return error;
    }
}
