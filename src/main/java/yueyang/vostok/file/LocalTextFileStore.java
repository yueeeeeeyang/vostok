package yueyang.vostok.file;

import yueyang.vostok.util.VKAssert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Default local text file store.
 */
public final class LocalTextFileStore implements VKFileStore {
    public static final String MODE = "local";
    private static final int STREAM_BUFFER_SIZE = 8 * 1024;

    private final Path root;
    private final Charset charset;

    public LocalTextFileStore(Path root) {
        this(root, StandardCharsets.UTF_8);
    }

    public LocalTextFileStore(Path root, Charset charset) {
        VKAssert.notNull(root, "Root path is null");
        VKAssert.notNull(charset, "Charset is null");
        this.root = root.toAbsolutePath().normalize();
        this.charset = charset;
        ensureDir(this.root);
    }

    @Override
    public String mode() {
        return MODE;
    }

    @Override
    public void create(String path, String content) {
        Path p = resolve(path);
        try {
            ensureParent(p);
            Files.writeString(p, orEmpty(content), charset, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw io("Create file failed: " + path, e);
        }
    }

    @Override
    public void write(String path, String content) {
        Path p = resolve(path);
        try {
            ensureParent(p);
            Files.writeString(p, orEmpty(content), charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw io("Write file failed: " + path, e);
        }
    }

    @Override
    public void update(String path, String content) {
        Path p = resolve(path);
        if (!Files.exists(p)) {
            throw new IllegalStateException("File does not exist: " + path);
        }
        write(path, content);
    }

    @Override
    public String read(String path) {
        Path p = resolve(path);
        try {
            return Files.readString(p, charset);
        } catch (IOException e) {
            throw io("Read file failed: " + path, e);
        }
    }

    @Override
    public String hash(String path, String algorithm) {
        VKAssert.notBlank(algorithm, "Algorithm is blank");
        Path p = resolve(path);
        VKAssert.isTrue(Files.isRegularFile(p), "Path is not a file: " + path);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm.trim());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported hash algorithm: " + algorithm, e);
        }
        try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ)) {
            byte[] buf = new byte[STREAM_BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
            return toHex(digest.digest());
        } catch (IOException e) {
            throw io("Hash file failed: " + path, e);
        }
    }

    @Override
    public boolean delete(String path) {
        return deleteRecursively(path);
    }

    @Override
    public boolean deleteIfExists(String path) {
        Path p = resolve(path);
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            throw io("DeleteIfExists failed: " + path, e);
        }
    }

    @Override
    public boolean deleteRecursively(String path) {
        Path p = resolve(path);
        if (!Files.exists(p)) {
            return false;
        }
        try {
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p, FileVisitOption.FOLLOW_LINKS)) {
                    s.sorted(Comparator.reverseOrder()).forEach(this::deleteSingle);
                }
                return true;
            }
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            throw io("Delete failed: " + path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    @Override
    public boolean isFile(String path) {
        return Files.isRegularFile(resolve(path));
    }

    @Override
    public boolean isDirectory(String path) {
        return Files.isDirectory(resolve(path));
    }

    @Override
    public void append(String path, String content) {
        Path p = resolve(path);
        try {
            ensureParent(p);
            Files.writeString(p, orEmpty(content), charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw io("Append file failed: " + path, e);
        }
    }

    @Override
    public List<String> readLines(String path) {
        Path p = resolve(path);
        try {
            return Files.readAllLines(p, charset);
        } catch (IOException e) {
            throw io("Read lines failed: " + path, e);
        }
    }

    @Override
    public void writeLines(String path, List<String> lines) {
        VKAssert.notNull(lines, "Lines is null");
        Path p = resolve(path);
        try {
            ensureParent(p);
            Files.write(p, lines, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw io("Write lines failed: " + path, e);
        }
    }

    @Override
    public List<VKFileInfo> list(String path, boolean recursive) {
        return walk(path, recursive, null);
    }

    @Override
    public List<VKFileInfo> walk(String path, boolean recursive, Predicate<VKFileInfo> filter) {
        Path p = resolve(path);
        if (!Files.exists(p)) {
            return List.of();
        }
        Predicate<VKFileInfo> predicate = filter == null ? info -> true : filter;
        if (!Files.isDirectory(p)) {
            VKFileInfo info = toInfo(p);
            return predicate.test(info) ? List.of(info) : List.of();
        }
        try (Stream<Path> s = recursive ? Files.walk(p).skip(1) : Files.list(p)) {
            return s.map(this::toInfo).filter(predicate).toList();
        } catch (IOException e) {
            throw io("List files failed: " + path, e);
        }
    }

    @Override
    public void mkdir(String path) {
        Path p = resolve(path);
        try {
            Files.createDirectory(p);
        } catch (IOException e) {
            throw io("Create directory failed: " + path, e);
        }
    }

    @Override
    public void mkdirs(String path) {
        ensureDir(resolve(path));
    }

    @Override
    public void rename(String path, String newName) {
        VKAssert.notBlank(newName, "New name is blank");
        VKAssert.isTrue(!newName.contains("/") && !newName.contains("\\"), "New name cannot contain path separator");
        Path p = resolve(path);
        Path target = p.resolveSibling(newName);
        try {
            Files.move(p, target);
        } catch (IOException e) {
            throw io("Rename failed: " + path + " -> " + newName, e);
        }
    }

    @Override
    public void copy(String sourcePath, String targetPath, boolean replaceExisting) {
        Path source = resolve(sourcePath);
        Path target = resolve(targetPath);
        try {
            ensureParent(target);
            if (replaceExisting) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, target);
            }
        } catch (IOException e) {
            throw io("Copy file failed: " + sourcePath + " -> " + targetPath, e);
        }
    }

    @Override
    public void move(String sourcePath, String targetPath, boolean replaceExisting) {
        Path source = resolve(sourcePath);
        Path target = resolve(targetPath);
        try {
            ensureParent(target);
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        } catch (IOException e) {
            throw io("Move file failed: " + sourcePath + " -> " + targetPath, e);
        }
    }

    @Override
    public void copyDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy) {
        VKAssert.notNull(strategy, "Conflict strategy is null");
        Path source = resolve(sourceDir);
        Path target = resolve(targetDir);
        VKAssert.isTrue(Files.exists(source), "Source directory does not exist: " + sourceDir);
        VKAssert.isTrue(Files.isDirectory(source), "Source path is not a directory: " + sourceDir);
        handleTargetConflict(target, strategy, "copyDir");
        copyDirectoryTree(source, target, strategy);
    }

    @Override
    public void moveDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy) {
        VKAssert.notNull(strategy, "Conflict strategy is null");
        Path source = resolve(sourceDir);
        Path target = resolve(targetDir);
        VKAssert.isTrue(Files.exists(source), "Source directory does not exist: " + sourceDir);
        VKAssert.isTrue(Files.isDirectory(source), "Source path is not a directory: " + sourceDir);
        if (Files.exists(target)) {
            if (strategy == VKFileConflictStrategy.SKIP) {
                return;
            }
            if (strategy == VKFileConflictStrategy.FAIL) {
                throw new IllegalStateException("Target directory already exists: " + targetDir);
            }
            deleteRecursivelyPath(target);
        }
        try {
            ensureParent(target);
            Files.move(source, target);
        } catch (IOException e) {
            copyDirectoryTree(source, target, VKFileConflictStrategy.OVERWRITE);
            deleteRecursivelyPath(source);
        }
    }

    @Override
    public void touch(String path) {
        Path p = resolve(path);
        try {
            ensureParent(p);
            if (Files.exists(p)) {
                Files.setLastModifiedTime(p, FileTime.from(Instant.now()));
                return;
            }
            Files.writeString(p, "", charset, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw io("Touch file failed: " + path, e);
        }
    }

    @Override
    public long size(String path) {
        Path p = resolve(path);
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw io("Get file size failed: " + path, e);
        }
    }

    @Override
    public Instant lastModified(String path) {
        Path p = resolve(path);
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            throw io("Get file lastModified failed: " + path, e);
        }
    }

    @Override
    public void zip(String sourcePath, String zipPath) {
        Path source = resolve(sourcePath);
        Path zip = resolve(zipPath);
        VKAssert.isTrue(Files.exists(source), "Source path does not exist: " + sourcePath);
        VKAssert.isTrue(!source.equals(zip), "Source path and zip path cannot be the same");
        try {
            ensureParent(zip);
            try (OutputStream os = Files.newOutputStream(zip, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                 ZipOutputStream zos = new ZipOutputStream(os, charset)) {
                if (Files.isDirectory(source)) {
                    zipDirectory(source, zip, zos);
                } else {
                    zipFile(source, source.getFileName().toString(), zos);
                }
            }
        } catch (IOException e) {
            throw io("Zip failed: " + sourcePath + " -> " + zipPath, e);
        }
    }

    @Override
    public void unzip(String zipPath, String targetDir, boolean replaceExisting) {
        Path zip = resolve(zipPath);
        Path target = resolve(targetDir);
        VKAssert.isTrue(Files.exists(zip), "Zip file does not exist: " + zipPath);
        try {
            ensureDir(target);
            try (InputStream is = Files.newInputStream(zip, StandardOpenOption.READ);
                 ZipInputStream zis = new ZipInputStream(is, charset)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path out = safeZipTarget(target, entry.getName());
                    if (entry.isDirectory()) {
                        ensureDir(out);
                    } else {
                        ensureParent(out);
                        writeZipEntry(zis, out, replaceExisting);
                        if (entry.getLastModifiedTime() != null) {
                            Files.setLastModifiedTime(out, entry.getLastModifiedTime());
                        }
                    }
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            throw io("Unzip failed: " + zipPath + " -> " + targetDir, e);
        }
    }

    @Override
    public VKFileWatchHandle watch(String path, VKFileWatchListener listener) {
        VKAssert.notNull(listener, "Watch listener is null");
        Path watched = resolve(path);
        VKAssert.isTrue(Files.exists(watched), "Watch path does not exist: " + path);
        Path watchDir = Files.isDirectory(watched) ? watched : watched.getParent();
        VKAssert.notNull(watchDir, "Watch path parent is null");
        String fileName = Files.isDirectory(watched) ? null : watched.getFileName().toString();

        try {
            WatchService ws = FileSystems.getDefault().newWatchService();
            watchDir.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            AtomicBoolean running = new AtomicBoolean(true);
            Thread worker = new Thread(() -> watchLoop(ws, running, watchDir, fileName, listener), "vostok-file-watch");
            worker.setDaemon(true);
            worker.start();
            return () -> {
                running.set(false);
                try {
                    ws.close();
                } catch (IOException ignore) {
                }
                worker.interrupt();
            };
        } catch (IOException e) {
            throw io("Watch register failed: " + path, e);
        }
    }

    private VKFileInfo toInfo(Path p) {
        try {
            boolean dir = Files.isDirectory(p);
            long size = dir ? 0L : Files.size(p);
            Instant lastModified = Files.getLastModifiedTime(p).toInstant();
            return new VKFileInfo(relativePath(p), dir, size, lastModified);
        } catch (IOException e) {
            throw io("Read file metadata failed: " + p, e);
        }
    }

    private String relativePath(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        if (abs.startsWith(root)) {
            return root.relativize(abs).toString().replace('\\', '/');
        }
        return abs.toString().replace('\\', '/');
    }

    private Path resolve(String rawPath) {
        VKAssert.notBlank(rawPath, "Path is blank");
        Path p = Path.of(rawPath.trim());
        Path resolved = p.isAbsolute() ? p.toAbsolutePath().normalize() : root.resolve(p).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes root directory: " + rawPath);
        }
        return resolved;
    }

    private void ensureParent(Path p) throws IOException {
        Path parent = p.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void ensureDir(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw io("Create directory failed: " + p, e);
        }
    }

    private UncheckedIOException io(String message, IOException e) {
        return new UncheckedIOException(message, e);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private void deleteSingle(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw io("Delete failed: " + p, e);
        }
    }

    private void zipDirectory(Path sourceDir, Path zipPath, ZipOutputStream zos) throws IOException {
        String base = sourceDir.getFileName() == null ? "" : sourceDir.getFileName().toString();
        try (Stream<Path> s = Files.walk(sourceDir)) {
            var it = s.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (p.equals(sourceDir) || p.equals(zipPath)) {
                    continue;
                }
                Path rel = sourceDir.relativize(p);
                String entryName = normalizeEntry((base.isEmpty() ? rel.toString() : base + "/" + rel).replace('\\', '/'));
                if (Files.isDirectory(p)) {
                    if (!entryName.endsWith("/")) {
                        entryName = entryName + "/";
                    }
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.closeEntry();
                } else {
                    zipFile(p, entryName, zos);
                }
            }
        }
    }

    private void zipFile(Path file, String entryName, ZipOutputStream zos) throws IOException {
        ZipEntry entry = new ZipEntry(normalizeEntry(entryName));
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        zos.putNextEntry(entry);
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            transfer(in, zos);
        }
        zos.closeEntry();
    }

    private Path safeZipTarget(Path targetDir, String entryName) {
        String normalizedEntry = normalizeEntry(entryName);
        Path out = targetDir.resolve(normalizedEntry).normalize();
        if (!out.startsWith(targetDir)) {
            throw new IllegalArgumentException("Zip entry escapes target directory: " + entryName);
        }
        return out;
    }

    private void writeZipEntry(ZipInputStream zis, Path target, boolean replaceExisting) throws IOException {
        StandardOpenOption[] options = replaceExisting
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                : new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        try (OutputStream out = Files.newOutputStream(target, options)) {
            transfer(zis, out);
        }
    }

    private void transfer(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }

    private String normalizeEntry(String entryName) {
        if (entryName == null) {
            return "";
        }
        String v = entryName.replace('\\', '/');
        while (v.startsWith("/")) {
            v = v.substring(1);
        }
        return v;
    }

    private void copyDirectoryTree(Path source, Path target, VKFileConflictStrategy strategy) {
        try (Stream<Path> s = Files.walk(source)) {
            var it = s.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                Path rel = source.relativize(p);
                Path dst = target.resolve(rel);
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dst);
                    continue;
                }
                if (Files.exists(dst)) {
                    if (strategy == VKFileConflictStrategy.SKIP) {
                        continue;
                    }
                    if (strategy == VKFileConflictStrategy.FAIL) {
                        throw new IllegalStateException("Target file already exists: " + relativePath(dst));
                    }
                    Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    ensureParent(dst);
                    Files.copy(p, dst);
                }
            }
        } catch (IOException e) {
            throw io("Copy directory failed: " + source + " -> " + target, e);
        }
    }

    private void handleTargetConflict(Path target, VKFileConflictStrategy strategy, String op) {
        if (!Files.exists(target)) {
            return;
        }
        if (strategy == VKFileConflictStrategy.SKIP) {
            return;
        }
        if (strategy == VKFileConflictStrategy.FAIL) {
            throw new IllegalStateException("Target directory already exists: " + relativePath(target));
        }
        deleteRecursivelyPath(target);
    }

    private void deleteRecursivelyPath(Path p) {
        try (Stream<Path> s = Files.walk(p, FileVisitOption.FOLLOW_LINKS)) {
            s.sorted(Comparator.reverseOrder()).forEach(this::deleteSingle);
        } catch (IOException e) {
            throw io("Delete recursively failed: " + p, e);
        }
    }

    private void watchLoop(WatchService ws,
                           AtomicBoolean running,
                           Path watchDir,
                           String fileName,
                           VKFileWatchListener listener) {
        try {
            while (running.get()) {
                WatchKey key = ws.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    VKFileWatchEventType type = toEventType(event.kind());
                    Path context = event.context() instanceof Path ? (Path) event.context() : null;
                    if (context == null) {
                        continue;
                    }
                    if (fileName != null && !fileName.equals(context.getFileName().toString())) {
                        continue;
                    }
                    Path abs = watchDir.resolve(context).toAbsolutePath().normalize();
                    listener.onEvent(new VKFileWatchEvent(type, relativePath(abs), Instant.now()));
                }
                if (!key.reset()) {
                    break;
                }
            }
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ignore) {
        }
    }

    private VKFileWatchEventType toEventType(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return VKFileWatchEventType.CREATE;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return VKFileWatchEventType.MODIFY;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return VKFileWatchEventType.DELETE;
        }
        return VKFileWatchEventType.OVERFLOW;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
