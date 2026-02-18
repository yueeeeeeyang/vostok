package yueyang.vostok.file;

import yueyang.vostok.util.VKAssert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Default local text file store.
 */
public final class LocalTextFileStore implements VKFileStore {
    public static final String MODE = "local";

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
    public boolean delete(String path) {
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
        Path p = resolve(path);
        if (!Files.exists(p)) {
            return List.of();
        }
        if (!Files.isDirectory(p)) {
            return List.of(toInfo(p));
        }
        try (Stream<Path> s = recursive ? Files.walk(p).skip(1) : Files.list(p)) {
            return s.map(this::toInfo).toList();
        } catch (IOException e) {
            throw io("List files failed: " + path, e);
        }
    }

    @Override
    public void mkdirs(String path) {
        ensureDir(resolve(path));
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
}
