package yueyang.vostok.scan;

import yueyang.vostok.util.VKLog;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ClassScanner {
    private ClassScanner() {
    }

    @FunctionalInterface
    public interface EntityScanner {
        Set<Class<?>> scan(String... basePackages);
    }

    public static Set<Class<?>> scan(String... basePackages) {
        Set<String> prefixes = normalizePackages(basePackages);
        Set<Class<?>> result = new HashSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassScanner.class.getClassLoader();
        }

        if (prefixes.isEmpty()) {
            String cp = System.getProperty("java.class.path");
            if (cp != null && !cp.trim().isEmpty()) {
                String[] entries = cp.split(File.pathSeparator);
                for (String entry : entries) {
                    if (entry == null || entry.trim().isEmpty()) {
                        continue;
                    }
                    File file = new File(entry);
                    if (!file.exists()) {
                        continue;
                    }
                    if (file.isDirectory()) {
                        scanDirectory(file.toPath(), file.toPath(), prefixes, result);
                    } else if (entry.endsWith(".jar")) {
                        scanJar(file, prefixes, result);
                    }
                }
            }
            return result;
        }

        for (String pkg : prefixes) {
            String path = pkg.replace('.', '/');
            try {
                Enumeration<URL> urls = cl.getResources(path);
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    String protocol = url.getProtocol();
                    if ("file".equals(protocol)) {
                        Path dir = Path.of(url.toURI());
                        scanDirectory(dir, dir, prefixes, result);
                    } else if ("jar".equals(protocol)) {
                        JarURLConnection conn = (JarURLConnection) url.openConnection();
                        File jar = new File(conn.getJarFile().getName());
                        scanJar(jar, prefixes, result);
                    } else {
                        // ignore unknown protocol
                    }
                }
            } catch (Exception e) {
                VKLog.error("Failed to scan package: " + pkg, e);
            }
        }
        return result;
    }

    private static Set<String> normalizePackages(String... basePackages) {
        Set<String> prefixes = new HashSet<>();
        if (basePackages == null || basePackages.length == 0) {
            return prefixes;
        }
        for (String pkg : basePackages) {
            if (pkg == null || pkg.trim().isEmpty()) {
                continue;
            }
            prefixes.add(pkg.trim());
        }
        return prefixes;
    }

    private static void scanDirectory(Path root, Path dir, Set<String> prefixes, Set<Class<?>> result) {
        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                if (Files.isDirectory(path)) {
                    scanDirectory(root, path, prefixes, result);
                    return;
                }
                String name = path.getFileName().toString();
                if (!name.endsWith(".class")) {
                    return;
                }
                String className = toClassName(root, path);
                if (!acceptClass(className, prefixes)) {
                    return;
                }
                loadClass(className, result);
            });
        } catch (IOException e) {
            VKLog.error("Failed to scan directory: " + dir, e);
        }
    }

    private static void scanJar(File jarFile, Set<String> prefixes, Set<Class<?>> result) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || entry.isDirectory()) {
                    continue;
                }
                String className = name.replace('/', '.').substring(0, name.length() - 6);
                if (!acceptClass(className, prefixes)) {
                    continue;
                }
                loadClass(className, result);
            }
        } catch (IOException e) {
            VKLog.error("Failed to scan jar: " + jarFile.getAbsolutePath(), e);
        }
    }

    private static String toClassName(Path root, Path classFile) {
        Path relative = root.relativize(classFile);
        String path = relative.toString();
        String className = path.replace(File.separatorChar, '.');
        return className.substring(0, className.length() - 6);
    }

    private static boolean acceptClass(String className, Set<String> prefixes) {
        if (className.contains("$")) {
            return false;
        }
        if (prefixes.isEmpty()) {
            return true;
        }
        for (String prefix : prefixes) {
            if (className.startsWith(prefix + ".") || className.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void loadClass(String className, Set<Class<?>> result) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassScanner.class.getClassLoader();
            }
            Class<?> clazz = Class.forName(className, false, cl);
            result.add(clazz);
        } catch (Throwable e) {
            VKLog.warn("Skip class: " + className + " due to: " + e.getClass().getSimpleName());
        }
    }
}
