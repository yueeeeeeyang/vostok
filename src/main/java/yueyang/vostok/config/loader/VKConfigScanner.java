package yueyang.vostok.config.loader;

import yueyang.vostok.config.VKConfigOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
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

public final class VKConfigScanner {
    private VKConfigScanner() {
    }

    public static VKConfigScanResult scanDefaults(VKConfigOptions options) {
        Map<String, VKConfigSource> dedup = new LinkedHashMap<>();
        Set<Path> watchRoots = new LinkedHashSet<>();

        if (options.isScanUserDir()) {
            Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            addDirectory(userDir, dedup, watchRoots);
        }

        for (Path dir : options.getExtraScanDirs()) {
            addDirectory(dir, dedup, watchRoots);
        }

        if (options.isScanClasspath()) {
            addClasspath(options, dedup, watchRoots);
        }

        List<VKConfigSource> sorted = dedup.values().stream()
                .sorted(Comparator.comparing(VKConfigSource::sourceId))
                .toList();
        return new VKConfigScanResult(sorted, watchRoots);
    }

    private static void addClasspath(VKConfigOptions options, Map<String, VKConfigSource> dedup, Set<Path> watchRoots) {
        String cp = options.getClasspath();
        if (cp == null || cp.isBlank()) {
            return;
        }

        String[] entries = cp.split(java.io.File.pathSeparator);
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path path = Paths.get(entry).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                continue;
            }
            if (Files.isDirectory(path)) {
                addDirectory(path, dedup, watchRoots);
                continue;
            }
            String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jar")) {
                addJar(path, dedup);
            }
        }
    }

    private static void addDirectory(Path dir, Map<String, VKConfigSource> dedup, Set<Path> watchRoots) {
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
                String fileName = fileNameOf(name);
                String sourceId = "jar:" + jarPath.toAbsolutePath().normalize() + "!/" + name;
                try (InputStream in = jar.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    dedup.putIfAbsent(sourceId, VKConfigSource.ofBytes(sourceId, fileName, bytes));
                }
            }
        } catch (IOException ignore) {
        }
    }

    private static String fileNameOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static boolean isConfigFile(Path path) {
        return isConfigName(path.getFileName().toString());
    }

    private static boolean isConfigName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".properties") || lower.endsWith(".yml") || lower.endsWith(".yaml");
    }
}
