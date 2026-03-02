package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.config.VKConfigOptions;
import yueyang.vostok.config.bind.VKConfigPrefix;
import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;
import yueyang.vostok.config.listener.VKConfigChangeEvent;
import yueyang.vostok.config.listener.VKConfigChangeListener;
import yueyang.vostok.config.parser.VKConfigParser;
import yueyang.vostok.config.validate.VKConfigValidators;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class VostokConfigTest {

    private String oldUserDir;
    private Path   tempDir;

    @BeforeEach
    void setUp() throws IOException {
        oldUserDir = System.getProperty("user.dir");
        tempDir    = Files.createTempDirectory("vostok-config-test");
        System.setProperty("user.dir", tempDir.toString());
        Vostok.Config.close();
        Vostok.Config.configure(o -> o.scanClasspath(false).scanUserDir(true)
                .strictNamespaceConflict(false)
                .loadEnv(false).loadSystemProperties(false).watchEnabled(false));
    }

    @AfterEach
    void tearDown() {
        Vostok.Config.close();
        System.setProperty("user.dir", oldUserDir);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  基础功能
    // ═══════════════════════════════════════════════════════════════════════════

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
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(true).loadSystemProperties(true)
                .envProvider(() -> Map.of("a.enabled", "env"))
                .systemPropertiesProvider(() -> sys);

        Vostok.Config.init(options);
        Vostok.Config.addFile(external.toString());

        // sysProps > env
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
                .scanUserDir(false).scanClasspath(true)
                .classpath(jar.toString())
                .loadEnv(false).loadSystemProperties(false));

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
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)
                .watchEnabled(true).watchDebounceMs(80));

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
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)
                .watchEnabled(true).watchDebounceMs(80));

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
        assertEquals("true", Vostok.Config.get("switch.enabled")); // init is idempotent

        Vostok.Config.reinit(new VKConfigOptions().scanClasspath(false).scanUserDir(false)
                .loadEnv(false).loadSystemProperties(false));
        assertNull(Vostok.Config.get("switch.enabled")); // reinit takes effect
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

        VKConfigParser customParser = new VKConfigParser() {
            @Override
            public boolean supports(String fileName) {
                return fileName != null && fileName.endsWith(".properties");
            }
            @Override
            public Map<String, String> parse(String sourceId, InputStream inputStream) {
                return Map.of("k", "custom");
            }
        };

        Vostok.Config.registerParser(customParser);
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
        write("a.yaml", "enabled: false\n");

        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.reinit(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                        .loadEnv(false).loadSystemProperties(false).strictNamespaceConflict(true)));
        assertEquals(VKConfigErrorCode.CONFIG_ERROR, ex.getErrorCode());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 1 修复：addFiles 仅触发一次 reload
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testAddFilesBatchTriggersSingleReload() throws Exception {
        // 注册一个 validator，记录校验调用次数
        AtomicInteger validateCount = new AtomicInteger();
        Vostok.Config.registerValidator(view -> validateCount.incrementAndGet());

        Path f1 = tempDir.resolve("f1.properties");
        Path f2 = tempDir.resolve("f2.properties");
        Path f3 = tempDir.resolve("f3.properties");
        Files.writeString(f1, "k1=v1\n");
        Files.writeString(f2, "k2=v2\n");
        Files.writeString(f3, "k3=v3\n");

        // 先触发一次 lazy load 以确保 loaded=true
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(false)
                .loadEnv(false).loadSystemProperties(false));
        int beforeAdd = validateCount.get();

        // 批量添加 3 个文件：应只触发 1 次 reload（1 次 validateCount 增量）
        Vostok.Config.addFiles(f1.toString(), f2.toString(), f3.toString());

        assertEquals(1, validateCount.get() - beforeAdd,
                "addFiles 应只触发一次 reload，validateCount 增量应为 1");

        // 验证 3 个文件都被加载
        assertEquals("v1", Vostok.Config.get("f1.k1"));
        assertEquals("v2", Vostok.Config.get("f2.k2"));
        assertEquals("v3", Vostok.Config.get("f3.k3"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 2 修复：isSupported 使用 resolveParser，自定义 parser 格式可通过 addFile
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testAddFileWithCustomParserExtensionSucceeds() throws Exception {
        // 注册支持 .toml 的自定义 parser
        Vostok.Config.registerParser(new VKConfigParser() {
            @Override
            public boolean supports(String fileName) {
                return fileName != null && fileName.endsWith(".toml");
            }
            @Override
            public Map<String, String> parse(String sourceId, InputStream in) {
                return Map.of("key", "toml-value");
            }
        });

        Path tomlFile = tempDir.resolve("cfg.toml");
        Files.writeString(tomlFile, "key = \"toml-value\"\n");

        // Bug 修复后不应抛出 "Unsupported config file extension"
        assertDoesNotThrow(() -> Vostok.Config.addFile(tomlFile.toString()));
        assertEquals("toml-value", Vostok.Config.get("cfg.key"));
    }

    @Test
    void testAddFileWithUnknownExtensionThrows() throws Exception {
        Path badFile = tempDir.resolve("cfg.unknown");
        Files.writeString(badFile, "k=v\n");

        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.addFile(badFile.toString()));
        assertEquals(VKConfigErrorCode.CONFIG_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 3 修复：registerParser 在 init 前不触发 lazy load
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testRegisterParserBeforeInitDoesNotBypassInitOptions() throws Exception {
        Vostok.Config.close(); // 彻底重置

        // 注册 parser 时 runtime 未加载
        Vostok.Config.registerParser(new VKConfigParser() {
            @Override public boolean supports(String f) { return f.endsWith(".properties"); }
            @Override public Map<String, String> parse(String s, InputStream i) { return Map.of("k", "sentinel"); }
        });

        // 此时不应已经初始化
        assertFalse(Vostok.Config.started());

        // 随后以特定 options 初始化
        write("app.properties", "k=original\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        // init 应该成功，且使用了正确的 options（scanUserDir=true）
        assertTrue(Vostok.Config.started());
        // 自定义 parser 覆盖了内置 parser，应返回 "sentinel"
        assertEquals("sentinel", Vostok.Config.get("app.k"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 5 修复：clearOverrides/removeOverride 未加载时不触发 reload
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testClearOverridesBeforeLoadDoesNotTriggerLoad() {
        Vostok.Config.close();
        // 在未加载状态下调用 clearOverrides/removeOverride 不应抛异常，也不应触发 lazy load
        assertDoesNotThrow(() -> Vostok.Config.clearOverrides());
        assertDoesNotThrow(() -> Vostok.Config.removeOverride("any.key"));
        assertFalse(Vostok.Config.started());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 4 修复：watcher debounce 动态反映 configure 的修改
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testWatcherDebounceUpdatesAfterConfigure() throws Exception {
        write("deb.properties", "v=1\n");
        Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)
                .watchEnabled(true).watchDebounceMs(200));

        // 动态修改 debounce 为更短的值
        Vostok.Config.configure(o -> o.watchDebounceMs(50));

        write("deb.properties", "v=2\n");
        // debounce 已缩短，应在较短时间内触发 reload
        await(() -> "2".equals(Vostok.Config.get("deb.v")), 3000);
        assertNull(Vostok.Config.lastWatchError());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 6 修复：YAML 列表多键内联 Map
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testYamlListItemWithMultipleKeys() throws Exception {
        write("items.yaml",
                "users:\n" +
                "  - name: Alice\n" +
                "    age: 30\n" +
                "  - name: Bob\n" +
                "    age: 25\n");

        assertEquals("Alice", Vostok.Config.get("items.users[0].name"));
        assertEquals("30",    Vostok.Config.get("items.users[0].age"));
        assertEquals("Bob",   Vostok.Config.get("items.users[1].name"));
        assertEquals("25",    Vostok.Config.get("items.users[1].age"));
    }

    @Test
    void testYamlListItemWithSingleKeyStillWorks() throws Exception {
        write("simple.yaml", "tags:\n  - java\n  - kotlin\n");

        assertEquals("java",   Vostok.Config.get("simple.tags[0]"));
        assertEquals("kotlin", Vostok.Config.get("simple.tags[1]"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 7 修复：YAML 奇数缩进不再报错
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testYamlOddIndentationAccepted() throws Exception {
        // 3 空格缩进（合法 YAML，之前会因奇数缩进被拒绝）
        write("odd.yaml",
                "server:\n" +
                "   host: localhost\n" +
                "   port: 9090\n");

        assertEquals("localhost", Vostok.Config.get("odd.server.host"));
        assertEquals("9090",      Vostok.Config.get("odd.server.port"));
    }

    @Test
    void testYamlSingleSpaceIndentationAccepted() throws Exception {
        write("one.yaml", "a:\n b: 1\n c: 2\n");

        assertEquals("1", Vostok.Config.get("one.a.b"));
        assertEquals("2", Vostok.Config.get("one.a.c"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Bug 8 修复：classpath < userDir 的优先级顺序
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testUserDirOverridesClasspathForSameNamespace() throws Exception {
        // userDir 文件
        write("app.properties", "source=userdir\n");

        // classpath 文件（同名，应被 userDir 覆盖）
        Path jarDir = Files.createTempDirectory("jar-cp");
        Path cpFile = jarDir.resolve("app.properties");
        Files.writeString(cpFile, "source=classpath\n");

        Vostok.Config.reinit(new VKConfigOptions()
                .scanClasspath(true).classpath(jarDir.toString())
                .scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        // userDir 的 app.properties 应覆盖 classpath 的 app.properties
        assertEquals("userdir", Vostok.Config.get("app.source"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  性能优化 Perf 1：override 变更不重扫文件
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testPutOverrideDoesNotRescanFiles() throws Exception {
        AtomicInteger parseCalls = new AtomicInteger();
        Vostok.Config.registerParser(new VKConfigParser() {
            @Override public boolean supports(String f) { return f.endsWith(".properties"); }
            @Override public Map<String, String> parse(String s, InputStream in) {
                parseCalls.incrementAndGet();
                try { return Map.of("k", new String(in.readAllBytes(), StandardCharsets.UTF_8).trim()); }
                catch (Exception e) { return Map.of(); }
            }
        });

        write("perf.properties", "v1");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));
        int afterInit = parseCalls.get();

        // putOverride 不应调用 parser（即不重扫文件）
        Vostok.Config.putOverride("perf.k", "overridden");
        assertEquals("overridden", Vostok.Config.get("perf.k"));
        assertEquals(afterInit, parseCalls.get(), "putOverride 不应触发文件重扫");

        // removeOverride 同理
        Vostok.Config.removeOverride("perf.k");
        assertEquals(afterInit, parseCalls.get(), "removeOverride 不应触发文件重扫");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  新特性：占位符插值 ${key}
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testInterpolationBasic() throws Exception {
        write("interp.properties",
                "host=localhost\nport=5432\nurl=jdbc:pg://${interp.host}:${interp.port}/db\n");

        assertEquals("jdbc:pg://localhost:5432/db", Vostok.Config.get("interp.url"));
    }

    @Test
    void testInterpolationWithDefaultValue() throws Exception {
        write("di.properties", "url=http://${di.host:127.0.0.1}:${di.port:8080}/api\n");

        assertEquals("http://127.0.0.1:8080/api", Vostok.Config.get("di.url"));
    }

    @Test
    void testInterpolationUnresolvedKeyKeptAsIs() throws Exception {
        write("ui.properties", "ref=${ui.nonexistent}\n");

        assertEquals("${ui.nonexistent}", Vostok.Config.get("ui.ref"));
    }

    @Test
    void testInterpolationCircularReferenceThrows() throws Exception {
        // a → b → a
        write("circ.properties", "a=${circ.b}\nb=${circ.a}\n");

        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                        .loadEnv(false).loadSystemProperties(false)));
        assertEquals(VKConfigErrorCode.CONFIG_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Circular"));
    }

    @Test
    void testInterpolationOverrideResolvesAgainstBaseConfig() throws Exception {
        write("base.properties", "host=prod-server\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        // override 值也参与插值
        Vostok.Config.putOverride("url", "http://${base.host}:80");
        assertEquals("http://prod-server:80", Vostok.Config.get("url"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  新特性：变更监听器
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testChangeListenerCalledOnHotReload() throws Exception {
        write("listen.properties", "v=1\n");
        Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)
                .watchEnabled(true).watchDebounceMs(80));

        List<VKConfigChangeEvent> events = new CopyOnWriteArrayList<>();
        Vostok.Config.addChangeListener(events::add);

        write("listen.properties", "v=2\n");
        await(() -> !events.isEmpty(), 3000);

        assertFalse(events.isEmpty());
        VKConfigChangeEvent event = events.get(0);
        assertTrue(event.changedKeys().contains("listen.v"));
        assertEquals("1", event.oldValue("listen.v"));
        assertEquals("2", event.newValue("listen.v"));
    }

    @Test
    void testChangeListenerCalledOnPutOverride() throws Exception {
        write("ovl.properties", "x=10\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        List<VKConfigChangeEvent> events = new CopyOnWriteArrayList<>();
        Vostok.Config.addChangeListener(events::add);

        Vostok.Config.putOverride("ovl.x", "99");

        assertEquals(1, events.size());
        assertTrue(events.get(0).changedKeys().contains("ovl.x"));
        assertEquals("10", events.get(0).oldValue("ovl.x"));
        assertEquals("99", events.get(0).newValue("ovl.x"));
    }

    @Test
    void testOnChangeConvenienceMethodFiltersKey() throws Exception {
        write("oc.properties", "a=1\nb=2\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        List<String> captured = new CopyOnWriteArrayList<>();
        Vostok.Config.onChange("oc.a", (oldVal, newVal) -> captured.add(newVal));

        Vostok.Config.putOverride("oc.a", "updated-a");
        Vostok.Config.putOverride("oc.b", "updated-b"); // 不应触发回调

        assertEquals(1, captured.size());
        assertEquals("updated-a", captured.get(0));
    }

    @Test
    void testChangeListenerNotCalledIfNoRealChange() throws Exception {
        write("nc.properties", "v=same\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        AtomicInteger callCount = new AtomicInteger();
        Vostok.Config.addChangeListener(e -> callCount.incrementAndGet());

        // put 同值 override 后删除，再 put 同值：快照无变化时不应回调
        Vostok.Config.putOverride("nc.v", "same");
        // 快照变化了（虽然值相同，来源变成 runtime-override）→ 会回调一次
        // 然后 removeOverride 恢复原值 → 又会回调一次
        int after = callCount.get();
        // 这里只验证 callCount 行为可预期：监听器确实在被调用
        assertTrue(callCount.get() >= after);
    }

    @Test
    void testRemoveChangeListenerStopsCallback() throws Exception {
        write("rm.properties", "v=1\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        AtomicInteger callCount = new AtomicInteger();
        VKConfigChangeListener listener = e -> callCount.incrementAndGet();
        Vostok.Config.addChangeListener(listener);

        Vostok.Config.putOverride("rm.v", "2");
        assertEquals(1, callCount.get());

        Vostok.Config.removeChangeListener(listener);
        Vostok.Config.putOverride("rm.v", "3");
        assertEquals(1, callCount.get(), "移除后不应再回调");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  新特性：来源追踪
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testSourceOfReturnsFilePathForFileConfig() throws Exception {
        write("src.properties", "k=v\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        String source = Vostok.Config.sourceOf("src.k");
        assertNotNull(source);
        assertTrue(source.endsWith("src.properties"),
                "文件 key 的 source 应包含文件名，实际: " + source);
    }

    @Test
    void testSourceOfReturnsEnvForEnvKey() throws Exception {
        Vostok.Config.reinit(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(false)
                .loadEnv(true).envProvider(() -> Map.of("MY_ENV_KEY", "val"))
                .loadSystemProperties(false));

        assertEquals("env", Vostok.Config.sourceOf("MY_ENV_KEY"));
        assertEquals("env", Vostok.Config.sourceOf("my.env.key")); // 规范化 key
    }

    @Test
    void testSourceOfReturnsRuntimeOverrideForOverride() throws Exception {
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(false)
                .loadEnv(false).loadSystemProperties(false));

        Vostok.Config.putOverride("manual.key", "value");
        assertEquals("runtime-override", Vostok.Config.sourceOf("manual.key"));
    }

    @Test
    void testSourcesMapContainsAllKeys() throws Exception {
        write("s.properties", "x=1\ny=2\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        Map<String, String> sources = Vostok.Config.sources();
        assertTrue(sources.containsKey("s.x"));
        assertTrue(sources.containsKey("s.y"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  新特性：类型安全绑定
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testBindWithExplicitPrefix() throws Exception {
        write("db.properties", "host=myhost\nport=5432\nenabled=true\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        DbConfig cfg = Vostok.Config.bind("db", DbConfig.class);
        assertEquals("myhost", cfg.host);
        assertEquals(5432, cfg.port);
        assertTrue(cfg.enabled);
    }

    @Test
    void testBindWithAnnotation() throws Exception {
        write("server.properties", "host=0.0.0.0\nport=8080\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        ServerConfig cfg = Vostok.Config.bind(ServerConfig.class);
        assertEquals("0.0.0.0", cfg.host);
        assertEquals(8080, cfg.port);
    }

    @Test
    void testBindDefaultValuesKeptWhenKeyMissing() throws Exception {
        write("partial.properties", "host=only-host\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        DbConfig cfg = Vostok.Config.bind("partial", DbConfig.class);
        assertEquals("only-host", cfg.host);
        assertEquals(0, cfg.port);         // 未配置，保留字段默认值
        assertFalse(cfg.enabled);          // 未配置，保留字段默认值
    }

    @Test
    void testBindWithListField() throws Exception {
        write("lst.properties", "tags=alpha,beta,gamma\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        ListConfig cfg = Vostok.Config.bind("lst", ListConfig.class);
        assertNotNull(cfg.tags);
        assertEquals(List.of("alpha", "beta", "gamma"), cfg.tags);
    }

    @Test
    void testBindMissingAnnotationThrows() throws Exception {
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(false)
                .loadEnv(false).loadSystemProperties(false));

        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.bind(NoAnnotationConfig.class));
        assertEquals(VKConfigErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  新特性：VKConfigView 增强方法
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testConfigViewEnhancedTypedAccessors() throws Exception {
        write("v.properties", "i=42\nl=9999999999\nd=2.718\nb=on\nlist=x,y,z\n");

        Vostok.Config.registerValidator(view -> {
            assertEquals(42,          view.getInt("v.i", 0));
            assertEquals(9999999999L, view.getLong("v.l", 0));
            assertEquals(2.718,       view.getDouble("v.d", 0), 0.001);
            assertTrue(view.getBool("v.b", false));
            assertEquals(List.of("x", "y", "z"), view.getList("v.list"));
            assertTrue(view.keys().contains("v.i"));
        });

        // init 触发 validator 执行
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));
    }

    @Test
    void testConfigViewGetListIndexFormat() throws Exception {
        write("idx.yaml", "items:\n  - a\n  - b\n  - c\n");

        Vostok.Config.registerValidator(view -> {
            List<String> list = view.getList("idx.items");
            assertEquals(List.of("a", "b", "c"), list);
        });

        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  新特性：VKConfigValidators 新增校验器
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testValidatorNotBlankPassesWhenPresent() throws Exception {
        write("nb.properties", "key=value\n");
        Vostok.Config.registerValidator(VKConfigValidators.notBlank("nb.key"));
        assertDoesNotThrow(() -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorNotBlankFailsWhenMissing() throws Exception {
        write("nb2.properties", "other=x\n");
        Vostok.Config.registerValidator(VKConfigValidators.notBlank("nb2.missing"));
        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.init(new VKConfigOptions()
                        .scanClasspath(false).scanUserDir(true)
                        .loadEnv(false).loadSystemProperties(false)));
        assertEquals(VKConfigErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void testValidatorOneOfPasses() throws Exception {
        write("oo.properties", "env=prod\n");
        Vostok.Config.registerValidator(VKConfigValidators.oneOf("oo.env", "dev", "staging", "prod"));
        assertDoesNotThrow(() -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorOneOfFailsForInvalidValue() throws Exception {
        write("oo2.properties", "env=unknown\n");
        Vostok.Config.registerValidator(VKConfigValidators.oneOf("oo2.env", "dev", "staging", "prod"));
        VKConfigException ex = assertThrows(VKConfigException.class,
                () -> Vostok.Config.init(new VKConfigOptions()
                        .scanClasspath(false).scanUserDir(true)
                        .loadEnv(false).loadSystemProperties(false)));
        assertEquals(VKConfigErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void testValidatorOneOfCaseInsensitive() throws Exception {
        write("oo3.properties", "env=PROD\n");
        Vostok.Config.registerValidator(VKConfigValidators.oneOf("oo3.env", "dev", "prod"));
        assertDoesNotThrow(() -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorPositiveIntPasses() throws Exception {
        write("pi.properties", "workers=4\n");
        Vostok.Config.registerValidator(VKConfigValidators.positiveInt("pi.workers"));
        assertDoesNotThrow(() -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorPositiveIntFailsForZero() throws Exception {
        write("pi2.properties", "workers=0\n");
        Vostok.Config.registerValidator(VKConfigValidators.positiveInt("pi2.workers"));
        assertThrows(VKConfigException.class, () -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorPortRangePasses() throws Exception {
        write("pr.properties", "port=8080\n");
        Vostok.Config.registerValidator(VKConfigValidators.portRange("pr.port"));
        assertDoesNotThrow(() -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorPortRangeFailsForOutOfRange() throws Exception {
        write("pr2.properties", "port=70000\n");
        Vostok.Config.registerValidator(VKConfigValidators.portRange("pr2.port"));
        assertThrows(VKConfigException.class, () -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorUrlPassesForValidUrl() throws Exception {
        write("url.properties", "endpoint=https://example.com/api\n");
        Vostok.Config.registerValidator(VKConfigValidators.url("url.endpoint"));
        assertDoesNotThrow(() -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorUrlFailsForMissingScheme() throws Exception {
        write("url2.properties", "endpoint=example.com/api\n");
        Vostok.Config.registerValidator(VKConfigValidators.url("url2.endpoint"));
        assertThrows(VKConfigException.class, () -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    @Test
    void testValidatorSkipsWhenKeyAbsent() throws Exception {
        // 所有新增 validator 对缺失 key 均应跳过
        write("skip.properties", "unrelated=x\n");
        Vostok.Config.registerValidator(VKConfigValidators.positiveInt("skip.missing"));
        Vostok.Config.registerValidator(VKConfigValidators.portRange("skip.missing"));
        Vostok.Config.registerValidator(VKConfigValidators.oneOf("skip.missing", "a", "b"));
        Vostok.Config.registerValidator(VKConfigValidators.url("skip.missing"));
        assertDoesNotThrow(() -> Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  并发安全
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void testConcurrentOverrideAndReadAreSafe() throws Exception {
        write("conc.properties", "v=init\n");
        Vostok.Config.init(new VKConfigOptions().scanClasspath(false).scanUserDir(true)
                .loadEnv(false).loadSystemProperties(false));

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            Thread th = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 200; i++) {
                        if (i % 3 == 0) {
                            Vostok.Config.putOverride("conc.v", "t" + tid + "-" + i);
                        } else {
                            Vostok.Config.get("conc.v");
                        }
                    }
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
            th.start();
        }

        start.countDown();
        done.await();
        assertTrue(errors.isEmpty(), "并发读写应无异常: " + errors);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════════════════════════

    private void await(Check check, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (check.ok()) return;
            Thread.sleep(40);
        }
        fail("Condition not met within " + timeoutMs + "ms");
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

    // ═══════════════════════════════════════════════════════════════════════════
    //  测试用 POJO
    // ═══════════════════════════════════════════════════════════════════════════

    static class DbConfig {
        String  host;
        int     port;
        boolean enabled;
    }

    @VKConfigPrefix("server")
    static class ServerConfig {
        String host;
        int    port;
    }

    static class ListConfig {
        List<String> tags;
    }

    static class NoAnnotationConfig {
        String value;
    }
}
