package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.config.VKConfigOptions;
import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;
import yueyang.vostok.config.parser.VKConfigParser;
import yueyang.vostok.config.validate.VKConfigValidators;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class VostokConfigTest {
    private String oldUserDir;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        oldUserDir = System.getProperty("user.dir");
        tempDir = Files.createTempDirectory("vostok-config-test");
        System.setProperty("user.dir", tempDir.toString());
        Vostok.Config.close();
        Vostok.Config.configure(o -> o.scanClasspath(false).scanUserDir(true).strictNamespaceConflict(false)
                .loadEnv(false).loadSystemProperties(false).watchEnabled(false));
    }

    @AfterEach
    void tearDown() {
        Vostok.Config.close();
        System.setProperty("user.dir", oldUserDir);
    }

    @Test
    void testAutoLoadAllSupportedFilesAndNamespacePrefix() throws Exception {
        write("a.properties", "enabled=true\nport=8081\n");
        write("b.yml", "server:\n  host: localhost\n  workers: 4\n");
        write("c.yaml", "list:\n  - red\n  - blue\n");
        write("nested/d.properties", "name=inner\n");

        assertEquals("true", Vostok.Config.get("a.enabled"));
        assertEquals(8081, Vostok.Config.getInt("a.port", 0));
        assertEquals("localhost", Vostok.Config.get("b.server.host"));
        assertEquals(4, Vostok.Config.getInt("b.server.workers", 0));
        assertEquals("red", Vostok.Config.get("c.list[0]"));
        assertEquals("blue", Vostok.Config.get("c.list[1]"));
        assertEquals("inner", Vostok.Config.get("d.name"));
    }

    @Test
    void testPriorityOrderDefaultExternalEnvJvmRuntimeOverride() throws Exception {
        write("a.properties", "enabled=default\n");
        Path external = tempDir.resolve("outside/a.yaml");
        Files.createDirectories(external.getParent());
        Files.writeString(external, "enabled: external\n");

        Properties sys = new Properties();
        sys.setProperty("a.enabled", "jvm");

        VKConfigOptions options = new VKConfigOptions()
                .scanClasspath(false)
                .scanUserDir(true)
                .loadEnv(true)
                .loadSystemProperties(true)
                .envProvider(() -> Map.of("a.enabled", "env"))
                .systemPropertiesProvider(() -> sys);

        Vostok.Config.init(options);
        Vostok.Config.addFile(external.toString());

        assertEquals("jvm", Vostok.Config.get("a.enabled"));

        Vostok.Config.putOverride("a.enabled", "runtime");
        assertEquals("runtime", Vostok.Config.get("a.enabled"));

        Vostok.Config.removeOverride("a.enabled");
        assertEquals("jvm", Vostok.Config.get("a.enabled"));
    }

    @Test
    void testJarClasspathScanning() throws Exception {
        Path jar = tempDir.resolve("cfg.jar");
        createJarWithText(jar, "jarcfg.properties", "enabled=true\n");

        Vostok.Config.init(new VKConfigOptions()
                .scanUserDir(false)
                .scanClasspath(true)
                .classpath(jar.toString())
                .loadEnv(false)
                .loadSystemProperties(false));

        assertEquals("true", Vostok.Config.get("jarcfg.enabled"));
    }

    @Test
    void testValidationFailFastAtInit() throws Exception {
        write("app.properties", "port=99999\n");

        Vostok.Config.registerValidator(VKConfigValidators.required("app.host"));
        Vostok.Config.registerValidator(VKConfigValidators.intRange("app.port", 1, 65535));

        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                        .loadEnv(false).loadSystemProperties(false)));
        assertEquals(VKConfigErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void testValidationCrossField() throws Exception {
        write("r.properties", "min=2\nmax=1\n");

        Vostok.Config.registerValidator(VKConfigValidators.cross(
                "min_le_max",
                view -> view.getInt("r.min", Integer.MAX_VALUE) <= view.getInt("r.max", Integer.MIN_VALUE),
                "r.min must be <= r.max"
        ));

        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                        .loadEnv(false).loadSystemProperties(false)));
        assertEquals(VKConfigErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void testHotReloadSuccess() throws Exception {
        write("hot.properties", "enabled=true\n");
        Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false)
                .scanUserDir(true)
                .loadEnv(false)
                .loadSystemProperties(false)
                .watchEnabled(true)
                .watchDebounceMs(80));

        assertEquals("true", Vostok.Config.get("hot.enabled"));

        write("hot.properties", "enabled=false\n");
        await(() -> "false".equals(Vostok.Config.get("hot.enabled")), 3000);
        assertNull(Vostok.Config.lastWatchError());
    }

    @Test
    void testHotReloadRollbackOnValidationFailure() throws Exception {
        write("safe.properties", "value=5\n");
        Vostok.Config.registerValidator(VKConfigValidators.intRange("safe.value", 1, 10));
        Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false)
                .scanUserDir(true)
                .loadEnv(false)
                .loadSystemProperties(false)
                .watchEnabled(true)
                .watchDebounceMs(80));

        assertEquals(5, Vostok.Config.getInt("safe.value", 0));
        write("safe.properties", "value=100\n");

        await(() -> Vostok.Config.lastWatchError() != null, 3000);
        assertEquals(5, Vostok.Config.getInt("safe.value", 0));
    }

    @Test
    void testInitIdempotentAndReinitEffective() throws Exception {
        write("switch.properties", "enabled=true\n");

        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));
        assertEquals("true", Vostok.Config.get("switch.enabled"));

        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(false)
                .loadEnv(false).loadSystemProperties(false));
        assertEquals("true", Vostok.Config.get("switch.enabled"));

        Vostok.Config.reinit(new VKConfigOptions().scanClasspath(false).scanUserDir(false)
                .loadEnv(false).loadSystemProperties(false));
        assertNull(Vostok.Config.get("switch.enabled"));
    }

    @Test
    void testReadPathIsLowLockAndStableDuringReload() throws Exception {
        write("fast.properties", "value=1\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 5000; i++) {
                    Vostok.Config.get("fast.value");
                }
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        reader.start();

        for (int i = 0; i < 20; i++) {
            write("fast.properties", "value=" + i + "\n");
            Vostok.Config.reload();
        }

        done.await();
        assertNull(err.get());
    }

    @Test
    void testRegisterParserCanOverrideParseBehavior() throws Exception {
        write("override.properties", "k=v\n");
        assertEquals("v", Vostok.Config.get("override.k"));

        VKConfigParser parser = new VKConfigParser() {
            @Override
            public boolean supports(String fileName) {
                return fileName != null && fileName.endsWith(".properties");
            }

            @Override
            public Map<String, String> parse(String sourceId, InputStream inputStream) {
                return Map.of("k", "custom");
            }
        };

        Vostok.Config.registerParser(parser);
        assertEquals("custom", Vostok.Config.get("override.k"));
    }

    @Test
    void testTypedGettersAndList() throws Exception {
        write("types.properties", "i=7\nl=99\nd=3.14\nb=yes\nlist=a,b,c\n");

        assertEquals(7, Vostok.Config.getInt("types.i", 0));
        assertEquals(99L, Vostok.Config.getLong("types.l", 0));
        assertEquals(3.14d, Vostok.Config.getDouble("types.d", 0), 0.0001);
        assertTrue(Vostok.Config.getBool("types.b", false));
        assertEquals(3, Vostok.Config.getList("types.list").size());
    }

    @Test
    void testRequiredThrowsWhenMissing() {
        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.required("missing.key"));
        assertEquals(VKConfigErrorCode.KEY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void testStrictNamespaceConflict() throws Exception {
        write("a.properties", "enabled=true\n");
        Path external = tempDir.resolve("a.yaml");
        Files.writeString(external, "enabled: false\n");

        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.reinit(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                        .loadEnv(false).loadSystemProperties(false).strictNamespaceConflict(true)));
        assertEquals(VKConfigErrorCode.CONFIG_ERROR, ex.getErrorCode());
    }

    private void await(Check check, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(40);
        }
        fail("Condition not met within timeout");
    }

    private interface Check {
        boolean ok();
    }

    private void write(String relativePath, String content) throws Exception {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private void createJarWithText(Path jarFile, String entryName, String content) throws Exception {
        Files.deleteIfExists(jarFile);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            JarEntry entry = new JarEntry(entryName);
            jos.putNextEntry(entry);
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }
}
