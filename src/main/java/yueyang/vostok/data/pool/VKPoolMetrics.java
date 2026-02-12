package yueyang.vostok.data.pool;

/**
 * 连接池指标。
 */
public class VKPoolMetrics {
    private final String name;
    private final int total;
    private final int active;
    private final int idle;

    public VKPoolMetrics(String name, int total, int active, int idle) {
        this.name = name;
        this.total = total;
        this.active = active;
        this.idle = idle;
    }

    
    public String getName() {
        return name;
    }

    
    public int getTotal() {
        return total;
    }

    
    public int getActive() {
        return active;
    }

    
    public int getIdle() {
        return idle;
    }
}
