package yueyang.vostok.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Runtime options for Config module.
 */
public class VKConfigOptions {
    private boolean scanClasspath = true;
    private boolean scanUserDir = true;
    private boolean strictNamespaceConflict = false;
    private final List<Path> extraScanDirs = new ArrayList<>();

    private boolean loadEnv = true;
    private boolean loadSystemProperties = true;

    private boolean watchEnabled = false;
    private long watchDebounceMs = 300;

    private String classpath = System.getProperty("java.class.path", "");
    private Supplier<Map<String, String>> envProvider = System::getenv;
    private Supplier<Properties> systemPropertiesProvider = System::getProperties;

    public boolean isScanClasspath() {
        return scanClasspath;
    }

    public VKConfigOptions scanClasspath(boolean scanClasspath) {
        this.scanClasspath = scanClasspath;
        return this;
    }

    public boolean isScanUserDir() {
        return scanUserDir;
    }

    public VKConfigOptions scanUserDir(boolean scanUserDir) {
        this.scanUserDir = scanUserDir;
        return this;
    }

    public boolean isStrictNamespaceConflict() {
        return strictNamespaceConflict;
    }

    public VKConfigOptions strictNamespaceConflict(boolean strictNamespaceConflict) {
        this.strictNamespaceConflict = strictNamespaceConflict;
        return this;
    }

    public List<Path> getExtraScanDirs() {
        return List.copyOf(extraScanDirs);
    }

    public VKConfigOptions addScanDir(Path dir) {
        if (dir != null) {
            this.extraScanDirs.add(dir.toAbsolutePath().normalize());
        }
        return this;
    }

    public boolean isLoadEnv() {
        return loadEnv;
    }

    public VKConfigOptions loadEnv(boolean loadEnv) {
        this.loadEnv = loadEnv;
        return this;
    }

    public boolean isLoadSystemProperties() {
        return loadSystemProperties;
    }

    public VKConfigOptions loadSystemProperties(boolean loadSystemProperties) {
        this.loadSystemProperties = loadSystemProperties;
        return this;
    }

    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    public VKConfigOptions watchEnabled(boolean watchEnabled) {
        this.watchEnabled = watchEnabled;
        return this;
    }

    public long getWatchDebounceMs() {
        return watchDebounceMs;
    }

    public VKConfigOptions watchDebounceMs(long watchDebounceMs) {
        this.watchDebounceMs = Math.max(50, watchDebounceMs);
        return this;
    }

    public String getClasspath() {
        return classpath;
    }

    public VKConfigOptions classpath(String classpath) {
        this.classpath = classpath == null ? "" : classpath;
        return this;
    }

    public Supplier<Map<String, String>> getEnvProvider() {
        return envProvider;
    }

    public VKConfigOptions envProvider(Supplier<Map<String, String>> envProvider) {
        if (envProvider != null) {
            this.envProvider = envProvider;
        }
        return this;
    }

    public Supplier<Properties> getSystemPropertiesProvider() {
        return systemPropertiesProvider;
    }

    public VKConfigOptions systemPropertiesProvider(Supplier<Properties> systemPropertiesProvider) {
        if (systemPropertiesProvider != null) {
            this.systemPropertiesProvider = systemPropertiesProvider;
        }
        return this;
    }

    public VKConfigOptions copy() {
        VKConfigOptions copy = new VKConfigOptions();
        copy.scanClasspath = this.scanClasspath;
        copy.scanUserDir = this.scanUserDir;
        copy.strictNamespaceConflict = this.strictNamespaceConflict;
        copy.extraScanDirs.addAll(this.extraScanDirs);
        copy.loadEnv = this.loadEnv;
        copy.loadSystemProperties = this.loadSystemProperties;
        copy.watchEnabled = this.watchEnabled;
        copy.watchDebounceMs = this.watchDebounceMs;
        copy.classpath = this.classpath;
        copy.envProvider = this.envProvider;
        copy.systemPropertiesProvider = this.systemPropertiesProvider;
        return copy;
    }
}
