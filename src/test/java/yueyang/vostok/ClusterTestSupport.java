package yueyang.vostok;

import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.core.VKClusterRuntime;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClusterTestSupport {
    private ClusterTestSupport() {
    }

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate free port", e);
        }
    }

    static String clusterName() {
        return "cluster-" + UUID.randomUUID();
    }

    static VKClusterConfig config(String clusterName, String secret, String nodeId, int port, String... seeds) {
        return new VKClusterConfig()
                .clusterName(clusterName)
                .clusterSecret(secret)
                .nodeId(nodeId)
                .bindHost("127.0.0.1")
                .bindPort(port)
                .advertiseHost("127.0.0.1")
                .advertisePort(port)
                .seedNodes(seeds)
                .heartbeatIntervalMs(100)
                .suspectTimeoutMs(300)
                .deadTimeoutMs(700)
                .syncIntervalMs(150)
                .connectTimeoutMs(300)
                .reliableAckTimeoutMs(120)
                .reliableRetryBaseMs(100)
                .reliableMaxRetries(2)
                .workerThreads(2)
                .ioThreads(2)
                .listenerQueueCapacity(8)
                .outboundQueueCapacity(64)
                .maxNodeCount(32)
                .shutdownWaitMs(500);
    }

    static void awaitCondition(BooleanSupplier condition, long timeoutMs, String message) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    static void hardCrash(VKClusterRuntime runtime) {
        try {
            Field schedulerField = VKClusterRuntime.class.getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            Object scheduler = schedulerField.get(runtime);
            if (scheduler instanceof java.util.concurrent.ScheduledExecutorService ses) {
                ses.shutdownNow();
            }
            Field connectionManagerField = VKClusterRuntime.class.getDeclaredField("connectionManager");
            connectionManagerField.setAccessible(true);
            Object connectionManager = connectionManagerField.get(runtime);
            Method close = connectionManager.getClass().getDeclaredMethod("close");
            close.setAccessible(true);
            close.invoke(connectionManager);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hard crash cluster runtime", e);
        }
    }

    static void sendProtocolVersionMismatchFrame(int port) {
        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            byte[] payload = new byte[8];
            java.nio.ByteBuffer.wrap(payload)
                    .putInt(999)
                    .putInt(1);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send invalid protocol frame", e);
        }
    }

    static void sendGarbageFrame(int port) {
        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            byte[] payload = "bad-frame".getBytes(StandardCharsets.UTF_8);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send garbage frame", e);
        }
    }
}
