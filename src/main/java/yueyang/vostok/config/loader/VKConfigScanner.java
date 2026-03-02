package yueyang.vostok.config.loader;

import yueyang.vostok.config.VKConfigOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 配置文件扫描器。
 * <p>
 * 扫描顺序决定合并优先级（后加入的 source 在 reloadFiles 中会覆盖前面的 source）：
 * <ol>
 *   <li>classpath（最低优先级）</li>
 *   <li>extraScanDirs</li>
 *   <li>userDir（最高文件优先级）</li>
 * </ol>
 * manualFiles 由调用方在 reloadFiles 末尾追加，拥有比上述三者更高的优先级。
 * 不按 sourceId 字典排序，保持扫描插入顺序，确保优先级语义正确。
 */
public final class VKConfigScanner {

    private VKConfigScanner() {
    }

    public static VKConfigScanResult scanDefaults(VKConfigOptions options) {
        // 使用 LinkedHashMap 去重（同一个绝对路径只保留第一次出现的 source），
        // 同时保持插入顺序，不做字典排序，以保证 classpath < extraDirs < userDir 的优先级。
        Map<String, VKConfigSource> dedup = new LinkedHashMap<>();
        Set<Path> watchRoots = new LinkedHashSet<>();

        // 1. classpath（最低优先级，最先放入，被后续同 namespace 文件覆盖）
        if (options.isScanClasspath()) {
            addClasspath(options, dedup, watchRoots);
        }

        // 2. 用户自定义扫描目录（中等优先级）
        for (Path dir : options.getExtraScanDirs()) {
            addDirectory(dir, dedup, watchRoots);
        }

        // 3. userDir（最高文件优先级，最后放入，在 reloadFiles 中 put 会覆盖前者）
        if (options.isScanUserDir()) {
            Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            addDirectory(userDir, dedup, watchRoots);
        }

        return new VKConfigScanResult(List.copyOf(dedup.values()), watchRoots);
    }

    private static void addClasspath(VKConfigOptions options,
                                     Map<String, VKConfigSource> dedup,
                                     Set<Path> watchRoots) {
        String cp = options.getClasspath();
        if (cp == null || cp.isBlank()) {
            return;
        }
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path path = Paths.get(entry).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                continue;
            }
            if (Files.isDirectory(path)) {
                addDirectory(path, dedup, watchRoots);
            } else if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                addJar(path, dedup);
            }
        }
    }

    private static void addDirectory(Path dir,
                                     Map<String, VKConfigSource> dedup,
                                     Set<Path> watchRoots) {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        watchRoots.add(dir);
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(VKConfigScanner::isConfigFile)
                  .forEach(file -> {
                      VKConfigSource source = VKConfigSource.ofFile(file);
                      dedup.putIfAbsent(source.sourceId(), source);
                  });
        } catch (Exception ignore) {
        }
    }

    /**
     * 扫描 JAR 文件，只取根层（不含 '/'）及最多一级子目录的 config 文件，
     * 避免把依赖库深层资源全部读入内存。
     * JAR 内容在扫描时一次性读取为字节数组，因为 JarFile 只能在 try-with-resources 内访问。
     */
    private static void addJar(Path jarPath, Map<String, VKConfigSource> dedup) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!isConfigName(name)) {
                    continue;
                }
                // 只保留根层（直接位于 JAR 根或仅一级目录下）
                int slashCount = countSlashes(name);
                if (slashCount > 1) {
                    continue;
                }
                String fileName = fileNameOf(name);
                String sourceId = "jar:" + jarPath.toAbsolutePath().normalize() + "!/" + name;
                try (InputStream in = jar.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    dedup.putIfAbsent(sourceId, VKConfigSource.ofBytes(sourceId, fileName, bytes));
                } catch (IOException ignore) {
                }
            }
        } catch (IOException ignore) {
        }
    }

    private static int countSlashes(String name) {
        int count = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '/') count++;
        }
        return count;
    }

    private static String fileNameOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static boolean isConfigFile(Path path) {
        return isConfigName(path.getFileName().toString());
    }

    static boolean isConfigName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".properties") || lower.endsWith(".yml") || lower.endsWith(".yaml");
    }
}
