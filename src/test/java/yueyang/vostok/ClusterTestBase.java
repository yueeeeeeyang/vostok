package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import java.util.ArrayList;
import java.util.List;

abstract class ClusterTestBase {
    private final List<VKClusterRuntime> runtimes = new ArrayList<>();

    protected VKClusterRuntime startRuntime(VKClusterConfig config) {
        VKClusterRuntime runtime = new VKClusterRuntime();
        runtime.init(config);
        runtimes.add(runtime);
        return runtime;
    }

    @AfterEach
    void closeRuntimes() {
        for (int i = runtimes.size() - 1; i >= 0; i--) {
            try {
                runtimes.get(i).close();
            } catch (Throwable ignore) {
            }
        }
        Vostok.Cluster.close();
    }
}
