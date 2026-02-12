package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.jdbc.VKRetryPolicy;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class VKRetryPolicyTest {
    @Test
    void testShouldRetryBySqlStatePrefix() {
        DataSourceConfig cfg = new DataSourceConfig()
                .retryEnabled(true)
                .maxRetries(2)
                .retrySqlStatePrefixes("08");
        VKRetryPolicy policy = new VKRetryPolicy(cfg);
        SQLException ex = new SQLException("conn", "08006");
        assertTrue(policy.shouldRetry(ex));
    }

    @Test
    void testBackoff() {
        DataSourceConfig cfg = new DataSourceConfig()
                .retryEnabled(true)
                .retryBackoffBaseMs(50)
                .retryBackoffMaxMs(200);
        VKRetryPolicy policy = new VKRetryPolicy(cfg);
        assertEquals(50, policy.backoffMs(1));
        assertEquals(100, policy.backoffMs(2));
        assertEquals(200, policy.backoffMs(3));
        assertEquals(200, policy.backoffMs(4));
    }
}
