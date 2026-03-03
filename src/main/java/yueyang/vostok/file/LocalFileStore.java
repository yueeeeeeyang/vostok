package yueyang.vostok.file;

import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import yueyang.vostok.security.VostokSecurity;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Default local file store.
 */
public final class LocalFileStore implements VKFileStore {
    public static final String MODE = "local";
    // Perf 1：缓冲区从 8KB 扩大到 64KB，降低系统调用频率，提升 transfer/hash/gzip 等流操作吞吐
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;

    // Perf 2：类加载时一次性缓存所有可写图片格式名称，避免 canWriteFormat() 每次调用 ImageIO.getWriterFormatNames()
    private static final Set<String> WRITABLE_FORMATS;
    static {
        Set<String> tmp = new HashSet<>();
        for (String name : ImageIO.getWriterFormatNames()) {
            if (name != null) {
                tmp.add(name.toLowerCase(Locale.ROOT));
            }
        }
        WRITABLE_FORMATS = Collections.unmodifiableSet(tmp);
    }

    private final Path root;
    private final Charset charset;
    // Perf 3：缓存 DateTimeFormatter，key = pattern + "\0" + zoneId，避免 suggestDatePath 每次重建 Formatter
    private final Map<String, DateTimeFormatter> FMT_CACHE = new ConcurrentHashMap<>();
    private final Set<VKFileWatchHandle> activeWatches = ConcurrentHashMap.newKeySet();

    public LocalFileStore(Path root) {
        this(root, StandardCharsets.UTF_8);
    }

    public LocalFileStore(Path root, Charset charset) {
        requireNotNull(root, "Root path is null");
        requireNotNull(charset, "Charset is null");
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
        requireExists(p, path);
        write(path, content);
    }

    @Override
    public String read(String path) {
        Path p = resolve(path);
        requireFile(p, path);
        try {
            return Files.readString(p, charset);
        } catch (IOException e) {
            throw io("Read file failed: " + path, e);
        }
    }

    @Override
    public byte[] readBytes(String path) {
        Path p = resolve(path);
        requireFile(p, path);
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw io("Read bytes failed: " + path, e);
        }
    }

    @Override
    public byte[] readRange(String path, long offset, int length) {
        if (offset < 0) {
            throw arg("Offset must be >= 0: " + offset);
        }
        if (length < 0) {
            throw arg("Length must be >= 0: " + length);
        }
        Path p = resolve(path);
        requireFile(p, path);
        if (length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(0, length));
        long copied = readRangeTo(path, offset, length, out);
        if (copied == 0) {
            return new byte[0];
        }
        return out.toByteArray();
    }

    @Override
    public long readRangeTo(String path, long offset, long length, OutputStream output) {
        if (offset < 0) {
            throw arg("Offset must be >= 0: " + offset);
        }
        if (length < 0) {
            throw arg("Length must be >= 0: " + length);
        }
        requireNotNull(output, "OutputStream is null");

        Path p = resolve(path);
        requireFile(p, path);
        if (length == 0) {
            return 0L;
        }
        try (SeekableByteChannel channel = Files.newByteChannel(p, StandardOpenOption.READ)) {
            long size = channel.size();
            if (offset >= size) {
                return 0L;
            }
            channel.position(offset);
            long remaining = Math.min(length, size - offset);
            ByteBuffer buffer = ByteBuffer.allocate(STREAM_BUFFER_SIZE);
            long copied = 0L;
            while (remaining > 0) {
                buffer.clear();
                int maxRead = (int) Math.min(buffer.capacity(), remaining);
                buffer.limit(maxRead);
                int n = channel.read(buffer);
                if (n == -1) {
                    break;
                }
                output.write(buffer.array(), 0, n);
                copied += n;
                remaining -= n;
            }
            return copied;
        } catch (IOException e) {
            throw io("Read range to output failed: " + path + " [offset=" + offset + ", length=" + length + "]", e);
        }
    }

    @Override
    public long readTo(String path, OutputStream output) {
        requireNotNull(output, "OutputStream is null");
        Path p = resolve(path);
        requireFile(p, path);
        try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ)) {
            return transferCount(in, output);
        } catch (IOException e) {
            throw io("Read to output failed: " + path, e);
        }
    }

    @Override
    public void writeBytes(String path, byte[] content) {
        requireNotNull(content, "Content bytes is null");
        Path p = resolve(path);
        try {
            ensureParent(p);
            Files.write(p, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw io("Write bytes failed: " + path, e);
        }
    }

    @Override
    public void appendBytes(String path, byte[] content) {
        requireNotNull(content, "Content bytes is null");
        Path p = resolve(path);
        try {
            ensureParent(p);
            Files.write(p, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw io("Append bytes failed: " + path, e);
        }
    }

    @Override
    public long writeFrom(String path, InputStream input) {
        return writeFrom(path, input, true);
    }

    @Override
    public long writeFrom(String path, InputStream input, boolean replaceExisting) {
        requireNotNull(input, "InputStream is null");
        Path p = resolve(path);
        try {
            ensureParent(p);
            StandardOpenOption[] opts = replaceExisting
                    ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
            try (OutputStream out = Files.newOutputStream(p, opts)) {
                return transferCount(input, out);
            }
        } catch (IOException e) {
            throw io("Write from stream failed: " + path, e);
        }
    }

    @Override
    public long appendFrom(String path, InputStream input) {
        requireNotNull(input, "InputStream is null");
        Path p = resolve(path);
        try {
            ensureParent(p);
            try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                return transferCount(input, out);
            }
        } catch (IOException e) {
            throw io("Append from stream failed: " + path, e);
        }
    }

    @Override
    public String suggestDatePath(String relativePath, Instant atTime, VKFileConfig config) {
        requireNotBlank(relativePath, "Relative path is blank");
        requireNotNull(atTime, "Instant is null");
        requireNotNull(config, "VKFileConfig is null");
        requireNotBlank(config.getDatePartitionPattern(), "Date partition pattern is blank");
        requireNotBlank(config.getDatePartitionZoneId(), "Date partition zoneId is blank");

        Path rel = Path.of(relativePath.trim());
        if (rel.isAbsolute()) {
            throw arg("Relative path must not be absolute: " + relativePath);
        }
        Path normalizedRel = rel.normalize();
        if (normalizedRel.toString().isBlank() || normalizedRel.startsWith("..")) {
            throw arg("Relative path is invalid: " + relativePath);
        }
        try {
            // Perf 3：通过 FMT_CACHE 复用 DateTimeFormatter，同一 pattern+zone 组合只构建一次
            String cacheKey = config.getDatePartitionPattern() + "\0" + config.getDatePartitionZoneId();
            DateTimeFormatter fmt = FMT_CACHE.computeIfAbsent(cacheKey, k ->
                    DateTimeFormatter.ofPattern(config.getDatePartitionPattern())
                            .withZone(ZoneId.of(config.getDatePartitionZoneId())));
            String datePrefix = fmt.format(atTime).replace('\\', '/');
            String relStr = normalizedRel.toString().replace('\\', '/');
            String merged = datePrefix.endsWith("/") ? datePrefix + relStr : datePrefix + "/" + relStr;

            Path check = root.resolve(merged).normalize();
            if (!check.startsWith(root)) {
                throw arg("Date partition path escapes root: " + relativePath);
            }
            return merged;
        } catch (VKFileException e) {
            throw e;
        } catch (Exception e) {
            throw new VKFileException(VKFileErrorCode.CONFIG_ERROR,
                    "Invalid date partition settings: pattern=" + config.getDatePartitionPattern()
                            + ", zoneId=" + config.getDatePartitionZoneId(), e);
        }
    }

    @Override
    public VKFileMigrateResult migrateBaseDir(String targetBaseDir, VKFileMigrateOptions options) {
        requireNotBlank(targetBaseDir, "Target baseDir is blank");
        requireNotNull(options, "VKFileMigrateOptions is null");
        requireNotNull(options.getMode(), "Migrate mode is null");
        requireNotNull(options.getConflictStrategy(), "Conflict strategy is null");
        if (options.getMaxRetries() < 0) {
            throw arg("Max retries must be >= 0");
        }
        if (options.getRetryIntervalMs() < 0) {
            throw arg("Retry interval must be >= 0");
        }
        if (options.getParallelism() <= 0) {
            throw arg("Parallelism must be > 0");
        }
        if (options.getQueueCapacity() <= 0) {
            throw arg("Queue capacity must be > 0");
        }

        Path sourceBase = root.toAbsolutePath().normalize();
        Path targetBase = Path.of(targetBaseDir.trim()).toAbsolutePath().normalize();
        if (targetBase.startsWith(sourceBase)) {
            throw arg("Target baseDir cannot be inside source baseDir: " + targetBaseDir);
        }
        CheckpointState checkpoint = prepareCheckpoint(sourceBase, options.getCheckpointFile(), options.isDryRun());

        long start = System.currentTimeMillis();
        VKFileMigrateResult r = new VKFileMigrateResult();
        r.fromBaseDir(sourceBase.toString());
        r.toBaseDir(targetBase.toString());
        if (!options.isDryRun()) {
            try {
                Files.createDirectories(targetBase);
            } catch (IOException e) {
                throw io("Create target baseDir failed: " + targetBaseDir, e);
            }
        }

        MigrateCounters c = options.getParallelism() <= 1
                ? migrateSequential(sourceBase, targetBase, options, checkpoint)
                : migrateParallel(sourceBase, targetBase, options, checkpoint);

        for (VKFileMigrateResult.Failure f : c.drainFailures()) {
            r.addFailure(f.path(), f.message());
        }
        r.totalFiles(c.totalFiles());
        r.totalDirs(c.totalDirs());
        r.migratedFiles(c.migratedFiles());
        r.skippedFiles(c.skippedFiles());
        r.failedFiles(c.failedFiles());
        r.totalBytes(c.totalBytes());
        r.migratedBytes(c.migratedBytes());
        r.durationMs(System.currentTimeMillis() - start);
        emitProgress(options, c, null, VKFileMigrateProgressStatus.DONE, 0, null);
        return r;
    }

    private MigrateCounters migrateSequential(Path sourceBase,
                                              Path targetBase,
                                              VKFileMigrateOptions options,
                                              CheckpointState checkpoint) {
        MigrateCounters c = new MigrateCounters();
        try {
            Files.walkFileTree(sourceBase, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(sourceBase)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!options.isIncludeHidden() && isHiddenSafe(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    c.incTotalDirs();
                    if (!options.isDryRun()) {
                        Path rel = sourceBase.relativize(dir);
                        Path dst = targetBase.resolve(rel).normalize();
                        try {
                            Files.createDirectories(dst);
                        } catch (IOException e) {
                            c.incFailedFiles();
                            c.addFailure(normalizeRelPath(rel), "Create target dir failed: " + e.getMessage());
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!options.isIncludeHidden() && isHiddenSafe(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path rel = sourceBase.relativize(file);
                    String relPath = normalizeRelPath(rel);
                    long sz = sizeSafe(file);
                    c.incTotalFiles();
                    c.addTotalBytes(Math.max(0L, sz));
                    if (checkpoint.enabled && checkpoint.completed.contains(relPath)) {
                        c.incSkippedFiles();
                        emitProgress(options, c, relPath, VKFileMigrateProgressStatus.SKIPPED, 1,
                                "Skipped by checkpoint");
                        return FileVisitResult.CONTINUE;
                    }
                    processMigrateFile(file, targetBase.resolve(rel).normalize(), relPath, sz, options, checkpoint, c);
                    return FileVisitResult.CONTINUE;
                }
            });
            if (!options.isDryRun()
                    && options.getMode() == VKFileMigrateMode.MOVE
                    && options.isDeleteEmptyDirsAfterMove()) {
                cleanupEmptyDirs(sourceBase);
            }
            return c;
        } catch (IOException e) {
            throw io("Migrate baseDir failed: " + sourceBase + " -> " + targetBase, e);
        }
    }

    private MigrateCounters migrateParallel(Path sourceBase,
                                            Path targetBase,
                                            VKFileMigrateOptions options,
                                            CheckpointState checkpoint) {
        MigrateCounters c = new MigrateCounters();
        BlockingQueue<MigrateTask> queue = new ArrayBlockingQueue<>(options.getQueueCapacity());
        AtomicReference<RuntimeException> workerError = new AtomicReference<>();
        Thread[] workers = new Thread[options.getParallelism()];
        for (int i = 0; i < workers.length; i++) {
            Thread worker = new Thread(() -> runMigrateWorker(queue, options, checkpoint, c, workerError),
                    "vostok-file-migrate-" + i);
            worker.setDaemon(true);
            worker.start();
            workers[i] = worker;
        }

        try {
            Files.walkFileTree(sourceBase, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(sourceBase)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!options.isIncludeHidden() && isHiddenSafe(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    c.incTotalDirs();
                    if (!options.isDryRun()) {
                        Path rel = sourceBase.relativize(dir);
                        Path dst = targetBase.resolve(rel).normalize();
                        try {
                            Files.createDirectories(dst);
                        } catch (IOException e) {
                            c.incFailedFiles();
                            c.addFailure(normalizeRelPath(rel), "Create target dir failed: " + e.getMessage());
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!options.isIncludeHidden() && isHiddenSafe(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path rel = sourceBase.relativize(file);
                    String relPath = normalizeRelPath(rel);
                    long sz = sizeSafe(file);
                    c.incTotalFiles();
                    c.addTotalBytes(Math.max(0L, sz));
                    if (checkpoint.enabled && checkpoint.completed.contains(relPath)) {
                        c.incSkippedFiles();
                        emitProgress(options, c, relPath, VKFileMigrateProgressStatus.SKIPPED, 1,
                                "Skipped by checkpoint");
                        return FileVisitResult.CONTINUE;
                    }
                    putTask(queue, new MigrateTask(file, targetBase.resolve(rel).normalize(), relPath, sz, false));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            workerError.compareAndSet(null, io("Migrate baseDir walk failed: " + sourceBase + " -> " + targetBase, e));
        } catch (RuntimeException e) {
            workerError.compareAndSet(null, e);
        } finally {
            for (int i = 0; i < workers.length; i++) {
                putTask(queue, MigrateTask.POISON);
            }
            joinWorkers(workers);
        }

        if (workerError.get() != null) {
            throw workerError.get();
        }
        if (!options.isDryRun()
                && options.getMode() == VKFileMigrateMode.MOVE
                && options.isDeleteEmptyDirsAfterMove()) {
            cleanupEmptyDirs(sourceBase);
        }
        return c;
    }

    private void runMigrateWorker(BlockingQueue<MigrateTask> queue,
                                  VKFileMigrateOptions options,
                                  CheckpointState checkpoint,
                                  MigrateCounters counters,
                                  AtomicReference<RuntimeException> workerError) {
        try {
            while (true) {
                MigrateTask task = queue.take();
                if (task.poison) {
                    return;
                }
                processMigrateFile(task.source, task.target, task.relPath, task.size, options, checkpoint, counters);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            workerError.compareAndSet(null, e);
        }
    }

    private void processMigrateFile(Path source,
                                    Path dst,
                                    String relPath,
                                    long size,
                                    VKFileMigrateOptions options,
                                    CheckpointState checkpoint,
                                    MigrateCounters c) {
        if (Files.exists(dst)) {
            if (options.getConflictStrategy() == VKFileConflictStrategy.SKIP) {
                c.incSkippedFiles();
                emitProgress(options, c, relPath, VKFileMigrateProgressStatus.SKIPPED, 1,
                        "Target already exists");
                return;
            }
            if (options.getConflictStrategy() == VKFileConflictStrategy.FAIL) {
                c.incFailedFiles();
                c.addFailure(relPath, "Target already exists");
                emitProgress(options, c, relPath, VKFileMigrateProgressStatus.FAILED, 1,
                        "Target already exists");
                return;
            }
        }

        if (options.isDryRun()) {
            c.incMigratedFiles();
            c.addMigratedBytes(Math.max(0L, size));
            emitProgress(options, c, relPath, VKFileMigrateProgressStatus.MIGRATED, 1, "Dry run");
            return;
        }

        int maxAttempts = options.getMaxRetries() + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ensureParent(dst);
                if (Files.isDirectory(dst)) {
                    deleteRecursivelyPath(dst);
                }
                long copied;
                if (options.isVerifyHash()) {
                    // verifyHash=true：需逐字节读取计算 hash，必须走流式拷贝
                    copied = copyFileByStream(source, dst, true);
                    String srcHash = hashByPath(source, "SHA-256");
                    String dstHash = hashByPath(dst, "SHA-256");
                    if (!srcHash.equals(dstHash)) {
                        throw state("Hash verify failed after migrate");
                    }
                } else {
                    // Perf 4：verifyHash=false 时使用 Files.copy() 零拷贝，减少用户态内存拷贝开销
                    Files.copy(source, dst, StandardCopyOption.REPLACE_EXISTING);
                    copied = Files.size(dst);
                }
                if (options.getMode() == VKFileMigrateMode.MOVE) {
                    Files.deleteIfExists(source);
                }
                c.incMigratedFiles();
                c.addMigratedBytes(copied);
                checkpointAppend(checkpoint, relPath);
                emitProgress(options, c, relPath, VKFileMigrateProgressStatus.MIGRATED, attempt, null);
                return;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    emitProgress(options, c, relPath, VKFileMigrateProgressStatus.RETRYING, attempt, e.getMessage());
                    sleepRetry(options.getRetryIntervalMs());
                    continue;
                }
                c.incFailedFiles();
                c.addFailure(relPath, e.getMessage());
                emitProgress(options, c, relPath, VKFileMigrateProgressStatus.FAILED, attempt, e.getMessage());
                return;
            }
        }
    }

    private void putTask(BlockingQueue<MigrateTask> queue, MigrateTask task) {
        try {
            queue.put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw state("Migrate task enqueue interrupted");
        }
    }

    private void joinWorkers(Thread[] workers) {
        for (Thread worker : workers) {
            if (worker == null) {
                continue;
            }
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public byte[] thumbnail(String imagePath, VKThumbnailOptions options) {
        requireNotNull(options, "VKThumbnailOptions is null");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        thumbnailInternal(imagePath, options, out);
        try {
            out.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw imageEncode("Thumbnail encode flush failed: " + imagePath, e);
        }
    }

    @Override
    public void thumbnailTo(String imagePath, String targetPath, VKThumbnailOptions options) {
        requireNotNull(options, "VKThumbnailOptions is null");
        Path target = resolve(targetPath);
        try {
            ensureParent(target);
            try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                thumbnailInternal(imagePath, options, out);
            }
        } catch (IOException e) {
            throw imageEncode("Thumbnail write failed: " + imagePath + " -> " + targetPath, e);
        }
    }

    @Override
    public String hash(String path, String algorithm) {
        requireNotBlank(algorithm, "Algorithm is blank");
        Path p = resolve(path);
        requireFile(p, path);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm.trim());
        } catch (NoSuchAlgorithmException e) {
            throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "Unsupported hash algorithm: " + algorithm, e);
        }
        try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ)) {
            // Perf 6：委托给 computeHash()，与 hashByPath() 共用同一套流读取逻辑
            return computeHash(in, digest);
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
        deleteRecursivelyPath(p);
        return true;
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
        requireFile(p, path);
        try {
            return Files.readAllLines(p, charset);
        } catch (IOException e) {
            throw io("Read lines failed: " + path, e);
        }
    }

    @Override
    public void writeLines(String path, List<String> lines) {
        requireNotNull(lines, "Lines is null");
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
        requireNotBlank(newName, "New name is blank");
        if (newName.contains("/") || newName.contains("\\")) {
            throw arg("New name cannot contain path separator");
        }
        Path p = resolve(path);
        requireExists(p, path);
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
        requireExists(source, sourcePath);
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
        requireExists(source, sourcePath);
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
        requireNotNull(strategy, "Conflict strategy is null");
        Path source = resolve(sourceDir);
        Path target = resolve(targetDir);
        requireExists(source, sourceDir);
        if (!Files.isDirectory(source)) {
            throw arg("Source path is not a directory: " + sourceDir);
        }
        if (Files.exists(target)) {
            if (strategy == VKFileConflictStrategy.SKIP) {
                return;
            }
            if (strategy == VKFileConflictStrategy.FAIL) {
                throw state("Target directory already exists: " + targetDir);
            }
            // Bug 2 修复：OVERWRITE 分支不再预删目标目录。
            // copyDirectoryTree 已通过 Files.copy + REPLACE_EXISTING 支持 OVERWRITE，
            // 预删操作会导致复制期间目标短暂不存在，产生数据丢失风险。
        }
        copyDirectoryTree(source, target, strategy);
    }

    @Override
    public void moveDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy) {
        requireNotNull(strategy, "Conflict strategy is null");
        Path source = resolve(sourceDir);
        Path target = resolve(targetDir);
        requireExists(source, sourceDir);
        if (!Files.isDirectory(source)) {
            throw arg("Source path is not a directory: " + sourceDir);
        }
        if (Files.exists(target)) {
            if (strategy == VKFileConflictStrategy.SKIP) {
                return;
            }
            if (strategy == VKFileConflictStrategy.FAIL) {
                throw state("Target directory already exists: " + targetDir);
            }
            // Bug 2 修复：OVERWRITE 分支不再预删目标目录，避免 move 中途失败导致数据丢失。
            // copyDirectoryTree 支持 OVERWRITE（REPLACE_EXISTING），无需预删。
        }
        try {
            ensureParent(target);
            Files.move(source, target);
        } catch (IOException moveEx) {
            // Bug 5 修复：跨设备移动时 Files.move 会抛 IOException，需退回 copy+delete 策略。
            // 若退回操作本身失败，将原始 IOException 附为 suppressed 一并抛出，避免静默吞掉。
            try {
                copyDirectoryTree(source, target, VKFileConflictStrategy.OVERWRITE);
                deleteRecursivelyPath(source);
            } catch (VKFileException fallbackEx) {
                fallbackEx.addSuppressed(moveEx);
                throw fallbackEx;
            }
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
        requireExists(p, path);
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw io("Get file size failed: " + path, e);
        }
    }

    @Override
    public Instant lastModified(String path) {
        Path p = resolve(path);
        requireExists(p, path);
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
        requireExists(source, sourcePath);
        if (source.equals(zip)) {
            throw arg("Source path and zip path cannot be the same");
        }
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
        unzip(zipPath, targetDir, VKUnzipOptions.defaults(replaceExisting));
    }

    @Override
    public void unzip(String zipPath, String targetDir, VKUnzipOptions options) {
        Path zip = resolve(zipPath);
        Path target = resolve(targetDir);
        requireExists(zip, zipPath);
        requireFile(zip, zipPath);
        VKUnzipOptions opt = options == null ? VKUnzipOptions.defaults(true) : options;
        validateUnzipOptions(opt);

        long totalExtracted = 0L;
        long entries = 0L;
        try {
            ensureDir(target);
            try (InputStream is = Files.newInputStream(zip, StandardOpenOption.READ);
                 ZipInputStream zis = new ZipInputStream(is, charset)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    entries++;
                    checkZipBomb(entries, opt.maxEntries(), "Zip entries exceed limit: " + opt.maxEntries());

                    Path out = safeZipTarget(target, entry.getName());
                    if (entry.isDirectory()) {
                        ensureDir(out);
                    } else {
                        ensureParent(out);
                        long extracted = writeZipEntry(zis, out, opt.replaceExisting(), opt.maxEntryUncompressedBytes(),
                                opt.maxTotalUncompressedBytes(), totalExtracted);
                        totalExtracted += extracted;
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
        return watch(path, false, listener);
    }

    @Override
    public VKFileWatchHandle watch(String path, boolean recursive, VKFileWatchListener listener) {
        requireNotNull(listener, "Watch listener is null");
        Path watched = resolve(path);
        requireExists(watched, path);

        Path watchRoot = Files.isDirectory(watched) ? watched : watched.getParent();
        if (watchRoot == null) {
            throw state("Watch path parent is null: " + path);
        }
        String fileName = Files.isDirectory(watched) ? null : watched.getFileName().toString();

        try {
            WatchService ws = FileSystems.getDefault().newWatchService();
            Map<WatchKey, Path> keyDirs = new HashMap<>();
            if (recursive) {
                registerRecursive(ws, watchRoot, keyDirs);
            } else {
                registerWatchDir(ws, watchRoot, keyDirs);
            }
            AtomicBoolean running = new AtomicBoolean(true);
            Thread worker = new Thread(() -> watchLoop(ws, running, watchRoot, fileName, recursive, keyDirs, listener), "vostok-file-watch");
            worker.setDaemon(true);
            worker.start();
            // Bug 6 修复：将内外两个 handle 合并为单一匿名类。
            // closed 字段保证 close() 幂等；close 时同时关闭 WatchService、中断 worker 并从 activeWatches 自我移除。
            AtomicBoolean closed = new AtomicBoolean(false);
            VKFileWatchHandle handle = new VKFileWatchHandle() {
                @Override
                public void close() {
                    if (!closed.compareAndSet(false, true)) {
                        return; // 幂等：已关闭则直接返回
                    }
                    running.set(false);
                    try {
                        ws.close();
                    } catch (IOException ignore) {
                    }
                    worker.interrupt();
                    activeWatches.remove(this);
                }
            };
            activeWatches.add(handle);
            return handle;
        } catch (IOException e) {
            throw io("Watch register failed: " + path, e);
        }
    }

    // -------------------------------------------------------------------------
    // Ext 1：目录总大小
    // -------------------------------------------------------------------------

    /**
     * 递归计算目录下所有普通文件的字节总大小，目录本身不计入。
     * 读取单个文件 size 失败时静默返回 0，保持整体可用性。
     */
    @Override
    public long totalSize(String dirPath) {
        Path p = resolve(dirPath);
        requireExists(p, dirPath);
        try (Stream<Path> s = Files.walk(p)) {
            return s.filter(Files::isRegularFile).mapToLong(this::sizeSafe).sum();
        } catch (IOException e) {
            throw io("Total size failed: " + dirPath, e);
        }
    }

    // -------------------------------------------------------------------------
    // Ext 2：临时文件
    // -------------------------------------------------------------------------

    /**
     * 在 tmp/ 子目录下创建临时文件，返回相对于 root 的路径。
     * prefix/suffix 语义同 {@link Files#createTempFile}。
     */
    @Override
    public String createTemp(String prefix, String suffix) {
        return createTemp("tmp", prefix, suffix);
    }

    /**
     * 在 subDir（相对 root）子目录下创建临时文件，返回相对路径。
     * subDir 为 null 时退回 tmp/。
     *
     * @throws VKFileException PATH_ERROR  子目录越界 root
     * @throws VKFileException IO_ERROR    创建目录或临时文件失败
     */
    @Override
    public String createTemp(String subDir, String prefix, String suffix) {
        String dir = (subDir == null || subDir.isBlank()) ? "tmp" : subDir.trim();
        Path dirPath = root.resolve(dir).normalize();
        if (!dirPath.startsWith(root)) {
            throw new VKFileException(VKFileErrorCode.PATH_ERROR, "Temp subDir escapes root: " + subDir);
        }
        try {
            Files.createDirectories(dirPath);
            Path temp = Files.createTempFile(dirPath, prefix, suffix);
            return relativePath(temp);
        } catch (IOException e) {
            throw io("Create temp file failed in " + dir, e);
        }
    }

    // -------------------------------------------------------------------------
    // Ext 3：GZip 压缩 / 解压
    // -------------------------------------------------------------------------

    /**
     * 将 sourcePath 指向的文件 GZip 压缩，输出到 gzPath。
     * source 与 target 路径不可相同。
     *
     * @throws VKFileException GZIP_ERROR  压缩 IO 失败
     */
    @Override
    public void gzip(String sourcePath, String gzPath) {
        Path source = resolve(sourcePath);
        Path gz = resolve(gzPath);
        requireExists(source, sourcePath);
        if (source.equals(gz)) {
            throw arg("Source path and gz path cannot be the same");
        }
        try {
            ensureParent(gz);
            try (InputStream in = Files.newInputStream(source, StandardOpenOption.READ);
                 GZIPOutputStream gout = new GZIPOutputStream(
                         Files.newOutputStream(gz, StandardOpenOption.CREATE,
                                 StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
                transfer(in, gout);
            }
        } catch (IOException e) {
            throw new VKFileException(VKFileErrorCode.GZIP_ERROR, "GZip compress failed: " + sourcePath, e);
        }
    }

    /**
     * 将 gzPath 指向的 GZip 文件解压到 targetPath。
     * source 与 target 路径不可相同。
     *
     * @throws VKFileException GZIP_ERROR  解压 IO 失败（含格式错误）
     */
    @Override
    public void gunzip(String gzPath, String targetPath) {
        Path gz = resolve(gzPath);
        Path target = resolve(targetPath);
        requireExists(gz, gzPath);
        if (gz.equals(target)) {
            throw arg("GZ path and target path cannot be the same");
        }
        try {
            ensureParent(target);
            try (GZIPInputStream gin = new GZIPInputStream(Files.newInputStream(gz, StandardOpenOption.READ));
                 OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                transfer(gin, out);
            }
        } catch (IOException e) {
            throw new VKFileException(VKFileErrorCode.GZIP_ERROR, "GZip decompress failed: " + gzPath, e);
        }
    }

    // -------------------------------------------------------------------------
    // 文件加密 / 解密（委托安全模块 AES-256-GCM vkf2 分块流式格式）
    // -------------------------------------------------------------------------

    /**
     * 使用安全模块（AES-256-GCM vkf2）流式加密 sourcePath 并写入 targetPath。
     *
     * <p>路径经 {@code resolve()} 规范化，防止路径逃逸；源文件须存在；
     * 实际加密委托给 {@link VostokSecurity#encryptFile}，调用前须确保 Security 模块已初始化。
     *
     * @throws VKFileException ENCRYPT_ERROR  加密失败（含密钥操作异常）
     * @throws VKFileException NOT_FOUND      源文件不存在
     * @throws VKFileException INVALID_ARGUMENT 路径相同或路径为空
     */
    @Override
    public void encryptFile(String sourcePath, String targetPath, String keyId) {
        Path source = resolve(sourcePath);
        Path target = resolve(targetPath);
        requireExists(source, sourcePath);
        if (source.equals(target)) {
            throw arg("Source path and target path cannot be the same");
        }
        try {
            ensureParent(target);
            // 委托 VostokSecurity.encryptFile 处理 DEK 生成、KEK 包裹、vkf2 格式写入
            VostokSecurity.encryptFile(source, target, keyId);
        } catch (VKFileException e) {
            throw e;
        } catch (Exception e) {
            throw new VKFileException(VKFileErrorCode.ENCRYPT_ERROR,
                    "File encrypt failed: " + sourcePath, e);
        }
    }

    /**
     * 解密 vkf2（或 vkf1 遗留）格式的 sourcePath 并写入 targetPath。
     *
     * <p>路径经 {@code resolve()} 规范化，防止路径逃逸；解密失败时临时文件被自动清除，
     * targetPath 不产生任何部分写入内容。委托给 {@link VostokSecurity#decryptFile}。
     *
     * @throws VKFileException ENCRYPT_ERROR  解密失败（含篡改检测、密钥不存在）
     * @throws VKFileException NOT_FOUND      源文件不存在
     * @throws VKFileException INVALID_ARGUMENT 路径相同或路径为空
     */
    @Override
    public void decryptFile(String sourcePath, String targetPath) {
        Path source = resolve(sourcePath);
        Path target = resolve(targetPath);
        requireExists(source, sourcePath);
        if (source.equals(target)) {
            throw arg("Source path and target path cannot be the same");
        }
        try {
            ensureParent(target);
            // VostokSecurity.decryptFile 内置临时文件机制：解密失败时不向 target 写入任何字节
            VostokSecurity.decryptFile(source, target);
        } catch (VKFileException e) {
            throw e;
        } catch (Exception e) {
            throw new VKFileException(VKFileErrorCode.ENCRYPT_ERROR,
                    "File decrypt failed: " + sourcePath, e);
        }
    }

    @Override
    public void close() {
        for (VKFileWatchHandle handle : activeWatches.toArray(new VKFileWatchHandle[0])) {
            try {
                handle.close();
            } catch (Exception ignore) {
            } finally {
                activeWatches.remove(handle);
            }
        }
    }

    private void validateUnzipOptions(VKUnzipOptions opt) {
        if (opt.maxEntries() < -1 || opt.maxTotalUncompressedBytes() < -1 || opt.maxEntryUncompressedBytes() < -1) {
            throw arg("Unzip limits must be -1 (unlimited) or >= 0");
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
        requireNotBlank(rawPath, "Path is blank");
        Path p = Path.of(rawPath.trim());
        Path resolved = p.isAbsolute() ? p.toAbsolutePath().normalize() : root.resolve(p).normalize();
        if (!resolved.startsWith(root)) {
            throw new VKFileException(VKFileErrorCode.PATH_ERROR, "Path escapes root directory: " + rawPath);
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
            throw new VKFileException(VKFileErrorCode.SECURITY_ERROR, "Zip entry escapes target directory: " + entryName);
        }
        return out;
    }

    private long writeZipEntry(ZipInputStream zis,
                               Path target,
                               boolean replaceExisting,
                               long maxEntryBytes,
                               long maxTotalBytes,
                               long currentTotalBytes) throws IOException {
        StandardOpenOption[] options = replaceExisting
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                : new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        try (OutputStream out = Files.newOutputStream(target, options)) {
            return transferWithLimits(zis, out, maxEntryBytes, maxTotalBytes, currentTotalBytes);
        }
    }

    private void transfer(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }

    private long transferCount(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        long total = 0L;
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            total += n;
        }
        return total;
    }

    private long transferWithLimits(InputStream in,
                                    OutputStream out,
                                    long maxEntryBytes,
                                    long maxTotalBytes,
                                    long currentTotalBytes) throws IOException {
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        long entryWritten = 0L;
        long total = currentTotalBytes;
        int n;
        while ((n = in.read(buffer)) != -1) {
            entryWritten += n;
            total += n;
            checkZipBomb(entryWritten, maxEntryBytes, "Zip entry exceeds max uncompressed bytes: " + maxEntryBytes);
            checkZipBomb(total, maxTotalBytes, "Zip total uncompressed bytes exceed limit: " + maxTotalBytes);
            out.write(buffer, 0, n);
        }
        return entryWritten;
    }

    private void checkZipBomb(long actual, long limit, String msg) {
        if (limit >= 0 && actual > limit) {
            throw new VKFileException(VKFileErrorCode.ZIP_BOMB_RISK, msg);
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
                        throw state("Target file already exists: " + relativePath(dst));
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

    private void deleteRecursivelyPath(Path p) {
        // Bug 1 修复：去掉 FOLLOW_LINKS，防止通过 symlink 删除 root 目录外部的文件/目录
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(this::deleteSingle);
        } catch (IOException e) {
            throw io("Delete recursively failed: " + p, e);
        }
    }

    private void watchLoop(WatchService ws,
                           AtomicBoolean running,
                           Path watchRoot,
                           String fileName,
                           boolean recursive,
                           Map<WatchKey, Path> keyDirs,
                           VKFileWatchListener listener) {
        try {
            while (running.get()) {
                WatchKey key = ws.take();
                Path eventBaseDir = keyDirs.get(key);
                if (eventBaseDir == null) {
                    key.reset();
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    VKFileWatchEventType type = toEventType(event.kind());
                    Path context = event.context() instanceof Path ? (Path) event.context() : null;
                    if (context == null) {
                        continue;
                    }
                    Path abs = eventBaseDir.resolve(context).toAbsolutePath().normalize();
                    if (fileName != null && !fileName.equals(abs.getFileName().toString())) {
                        continue;
                    }

                    if (recursive && type == VKFileWatchEventType.CREATE) {
                        tryRegisterNewDir(ws, abs, keyDirs);
                    }

                    if (!abs.startsWith(watchRoot)) {
                        continue;
                    }
                    listener.onEvent(new VKFileWatchEvent(type, relativePath(abs), Instant.now()));
                }
                if (!key.reset()) {
                    keyDirs.remove(key);
                    break;
                }
            }
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ignore) {
        }
    }

    private void registerRecursive(WatchService ws, Path root, Map<WatchKey, Path> keyDirs) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            var it = s.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (Files.isDirectory(p)) {
                    registerWatchDir(ws, p, keyDirs);
                }
            }
        }
    }

    private void registerWatchDir(WatchService ws, Path dir, Map<WatchKey, Path> keyDirs) throws IOException {
        WatchKey key = dir.register(ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        keyDirs.put(key, dir);
    }

    private void tryRegisterNewDir(WatchService ws, Path path, Map<WatchKey, Path> keyDirs) {
        try {
            if (Files.isDirectory(path)) {
                registerRecursive(ws, path, keyDirs);
            }
        } catch (IOException ignore) {
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

    private String thumbnailInternal(String imagePath, VKThumbnailOptions options, OutputStream output) {
        validateThumbnailOptions(options);
        Path source = resolve(imagePath);
        requireFile(source, imagePath);

        String requestedFormat = normalizeFormat(options.format());
        try (ImageInputStream iis = ImageIO.createImageInputStream(Files.newInputStream(source, StandardOpenOption.READ))) {
            if (iis == null) {
                throw imageDecode("Cannot open image input stream: " + imagePath, null);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new VKFileException(VKFileErrorCode.UNSUPPORTED_IMAGE_FORMAT, "Unsupported image format: " + imagePath);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int srcW = reader.getWidth(0);
                int srcH = reader.getHeight(0);
                long inputPixels = (long) srcW * srcH;
                if (inputPixels > options.maxInputPixels()) {
                    throw new VKFileException(VKFileErrorCode.IMAGE_LIMIT_EXCEEDED,
                            "Input image pixels exceed limit: " + inputPixels + " > " + options.maxInputPixels());
                }
                int[] outputSize = calcOutputSize(srcW, srcH, options);
                long outputPixels = (long) outputSize[0] * outputSize[1];
                if (outputPixels > options.maxOutputPixels()) {
                    throw new VKFileException(VKFileErrorCode.IMAGE_LIMIT_EXCEEDED,
                            "Output image pixels exceed limit: " + outputPixels + " > " + options.maxOutputPixels());
                }

                int subsampling = chooseSubsampling(srcW, srcH, outputSize[0], outputSize[1]);
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage src = reader.read(0, param);
                if (src == null) {
                    throw imageDecode("Failed to decode image: " + imagePath, null);
                }
                BufferedImage result = renderThumbnail(src, options);
                if (options.sharpen()) {
                    result = applySharpen(result);
                }

                String sourceFormat = normalizeFormat(reader.getFormatName());
                String outFormat = requestedFormat == null ? sourceFormat : requestedFormat;
                if (!canWriteFormat(outFormat)) {
                    throw new VKFileException(VKFileErrorCode.UNSUPPORTED_IMAGE_FORMAT, "Unsupported output format: " + outFormat);
                }
                writeImage(result, outFormat, options.quality(), output);
                return outFormat;
            } finally {
                reader.dispose();
            }
        } catch (VKFileException e) {
            throw e;
        } catch (IOException e) {
            throw imageDecode("Thumbnail decode failed: " + imagePath, e);
        }
    }

    private void validateThumbnailOptions(VKThumbnailOptions options) {
        if (options.width() <= 0 || options.height() <= 0) {
            throw arg("Thumbnail width/height must be > 0");
        }
        if (options.quality() < 0f || options.quality() > 1f) {
            throw arg("Thumbnail quality must be in [0,1]");
        }
        if (options.maxInputPixels() <= 0 || options.maxOutputPixels() <= 0) {
            throw arg("Thumbnail pixel limits must be > 0");
        }
    }

    private int[] calcOutputSize(int srcW, int srcH, VKThumbnailOptions options) {
        if (!options.keepAspectRatio()) {
            int w = options.upscale() ? options.width() : Math.min(options.width(), srcW);
            int h = options.upscale() ? options.height() : Math.min(options.height(), srcH);
            return new int[]{Math.max(1, w), Math.max(1, h)};
        }
        double wr = (double) options.width() / srcW;
        double hr = (double) options.height() / srcH;
        double scale = options.mode() == VKThumbnailMode.FILL ? Math.max(wr, hr) : Math.min(wr, hr);
        if (!options.upscale() && scale > 1d) {
            scale = 1d;
        }
        int w = Math.max(1, (int) Math.round(srcW * scale));
        int h = Math.max(1, (int) Math.round(srcH * scale));
        if (options.mode() == VKThumbnailMode.FILL) {
            return new int[]{options.width(), options.height()};
        }
        return new int[]{w, h};
    }

    private int chooseSubsampling(int srcW, int srcH, int targetW, int targetH) {
        int sx = Math.max(1, srcW / Math.max(1, targetW));
        int sy = Math.max(1, srcH / Math.max(1, targetH));
        int s = Math.min(sx, sy);
        int p = 1;
        while (p * 2 <= s) {
            p *= 2;
        }
        return Math.max(1, p);
    }

    private BufferedImage renderThumbnail(BufferedImage src, VKThumbnailOptions options) {
        if (!options.keepAspectRatio()) {
            int w = options.upscale() ? options.width() : Math.min(options.width(), src.getWidth());
            int h = options.upscale() ? options.height() : Math.min(options.height(), src.getHeight());
            return scaleImage(src, Math.max(1, w), Math.max(1, h), options.background());
        }

        if (options.mode() == VKThumbnailMode.FILL) {
            double wr = (double) options.width() / src.getWidth();
            double hr = (double) options.height() / src.getHeight();
            double scale = Math.max(wr, hr);
            if (!options.upscale() && scale > 1d) {
                scale = 1d;
            }
            int scaledW = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int scaledH = Math.max(1, (int) Math.round(src.getHeight() * scale));
            BufferedImage scaled = scaleImage(src, scaledW, scaledH, options.background());
            BufferedImage out = new BufferedImage(options.width(), options.height(), pickImageType(scaled));
            Graphics2D g = out.createGraphics();
            configureQuality(g);
            g.setComposite(AlphaComposite.Src);
            g.setColor(options.background());
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            int x = (out.getWidth() - scaled.getWidth()) / 2;
            int y = (out.getHeight() - scaled.getHeight()) / 2;
            g.drawImage(scaled, x, y, null);
            g.dispose();
            return out;
        }

        double wr = (double) options.width() / src.getWidth();
        double hr = (double) options.height() / src.getHeight();
        double scale = Math.min(wr, hr);
        if (!options.upscale() && scale > 1d) {
            scale = 1d;
        }
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        return scaleImage(src, w, h, options.background());
    }

    private BufferedImage scaleImage(BufferedImage src, int w, int h, Color bg) {
        BufferedImage out = new BufferedImage(w, h, pickImageType(src));
        Graphics2D g = out.createGraphics();
        configureQuality(g);
        g.setComposite(AlphaComposite.Src);
        g.setColor(bg == null ? Color.WHITE : bg);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void configureQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private int pickImageType(BufferedImage img) {
        int t = img.getType();
        return t == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : t;
    }

    private BufferedImage applySharpen(BufferedImage src) {
        float[] kernel = new float[]{
                0f, -1f, 0f,
                -1f, 5f, -1f,
                0f, -1f, 0f
        };
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }

    private boolean canWriteFormat(String format) {
        // Perf 2：直接查询类加载时已缓存的 WRITABLE_FORMATS，不再每次调用 ImageIO.getWriterFormatNames()
        return format != null && WRITABLE_FORMATS.contains(format.toLowerCase(Locale.ROOT));
    }

    private void writeImage(BufferedImage image, String format, float quality, OutputStream output) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new VKFileException(VKFileErrorCode.UNSUPPORTED_IMAGE_FORMAT, "No writer for format: " + format);
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
            if (ios == null) {
                throw imageEncode("Cannot open image output stream for format: " + format, null);
            }
            writer.setOutput(ios);
            ImageWriteParam wp = writer.getDefaultWriteParam();
            if (wp.canWriteCompressed() && quality >= 0f && quality <= 1f) {
                wp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                wp.setCompressionQuality(quality);
            }
            IIOImage iio = new IIOImage(image, null, null);
            writer.write(null, iio, wp);
            ios.flush();
        } catch (IOException e) {
            throw imageEncode("Write thumbnail failed: format=" + format, e);
        } finally {
            writer.dispose();
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return null;
        }
        // Bug 4 修复：去掉 "jpeg" → "jpg" 的硬编码映射。
        // 部分 JVM ImageIO 实现将 "jpeg" 注册为标准格式名称，强制改为 "jpg" 会导致找不到对应 writer 而失败。
        return format.trim().toLowerCase(Locale.ROOT);
    }

    private VKFileException imageDecode(String message, Throwable cause) {
        return cause == null
                ? new VKFileException(VKFileErrorCode.IMAGE_DECODE_ERROR, message)
                : new VKFileException(VKFileErrorCode.IMAGE_DECODE_ERROR, message, cause);
    }

    private VKFileException imageEncode(String message, Throwable cause) {
        return cause == null
                ? new VKFileException(VKFileErrorCode.IMAGE_ENCODE_ERROR, message)
                : new VKFileException(VKFileErrorCode.IMAGE_ENCODE_ERROR, message, cause);
    }

    private CheckpointState prepareCheckpoint(Path sourceBase, String checkpointFile, boolean dryRun) {
        if (dryRun || checkpointFile == null || checkpointFile.trim().isEmpty()) {
            return CheckpointState.disabled();
        }
        Path p = Path.of(checkpointFile.trim());
        // Bug 3 修复：原 if/else 两分支代码完全相同；现区分相对路径（解析到 sourceBase 下）和绝对路径（直接规范化）
        if (!p.isAbsolute()) {
            p = sourceBase.resolve(p).normalize();
        } else {
            p = p.normalize();
        }
        if (p.startsWith(sourceBase)) {
            throw arg("Checkpoint file must not be inside source baseDir: " + p);
        }
        try {
            Set<String> done = ConcurrentHashMap.newKeySet();
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (line != null && !line.isBlank()) {
                        done.add(line.trim());
                    }
                }
            }
            return new CheckpointState(true, p, done);
        } catch (IOException e) {
            throw io("Read checkpoint file failed: " + p, e);
        }
    }

    private void checkpointAppend(CheckpointState checkpoint, String relPath) throws IOException {
        if (!checkpoint.enabled) {
            return;
        }
        if (checkpoint.completed.contains(relPath)) {
            return;
        }
        // Perf 5：用 checkpoint 私有的 writeLock 代替 synchronized(this)，
        // 避免多线程并行 migrate 时对整个 LocalFileStore 实例加锁造成不必要的竞争。
        // 双重检查防止并发写入重复追加。
        synchronized (checkpoint.writeLock) {
            if (checkpoint.completed.contains(relPath)) {
                return;
            }
            ensureParent(checkpoint.path);
            Files.writeString(checkpoint.path, relPath + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            checkpoint.completed.add(relPath);
        }
    }

    private String normalizeRelPath(Path rel) {
        return rel.toString().replace('\\', '/');
    }

    private void emitProgress(VKFileMigrateOptions options,
                              MigrateCounters c,
                              String relPath,
                              VKFileMigrateProgressStatus status,
                              int attempt,
                              String message) {
        VKFileMigrateProgressListener listener = options.getProgressListener();
        if (listener == null) {
            return;
        }
        try {
            listener.onProgress(new VKFileMigrateProgress(
                    status,
                    relPath,
                    c.totalFiles(),
                    c.migratedFiles(),
                    c.skippedFiles(),
                    c.failedFiles(),
                    c.totalBytes(),
                    c.migratedBytes(),
                    attempt,
                    options.getMaxRetries(),
                    message
            ));
        } catch (Exception ignore) {
        }
    }

    private void sleepRetry(long retryIntervalMs) {
        if (retryIntervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isHiddenSafe(Path p) {
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }

    private long sizeSafe(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private long copyFileByStream(Path src, Path dst, boolean replaceExisting) throws IOException {
        StandardOpenOption[] opts = replaceExisting
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                : new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        try (InputStream in = Files.newInputStream(src, StandardOpenOption.READ);
             OutputStream out = Files.newOutputStream(dst, opts)) {
            return transferCount(in, out);
        }
    }

    private String hashByPath(Path p, String algorithm) throws IOException, NoSuchAlgorithmException {
        // Perf 6：委托给 computeHash()，与 hash() 共用同一套流读取逻辑，消除重复代码
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ)) {
            return computeHash(in, digest);
        }
    }

    /**
     * 从输入流读取全部字节并计算摘要，返回十六进制字符串。
     * hash() 与 hashByPath() 的公共实现，避免重复的缓冲区读取逻辑。
     */
    private String computeHash(InputStream in, MessageDigest digest) throws IOException {
        byte[] buf = new byte[STREAM_BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) != -1) {
            digest.update(buf, 0, n);
        }
        return toHex(digest.digest());
    }

    private void cleanupEmptyDirs(Path sourceBase) {
        try (Stream<Path> s = Files.walk(sourceBase)) {
            s.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(sourceBase))
                    .forEach(p -> {
                        try {
                            if (Files.isDirectory(p)) {
                                try (Stream<Path> children = Files.list(p)) {
                                    if (children.findAny().isEmpty()) {
                                        Files.deleteIfExists(p);
                                    }
                                }
                            }
                        } catch (IOException ignore) {
                        }
                    });
        } catch (IOException ignore) {
        }
    }

    private static final class MigrateCounters {
        private final LongAdder totalFiles = new LongAdder();
        private final LongAdder totalDirs = new LongAdder();
        private final LongAdder migratedFiles = new LongAdder();
        private final LongAdder skippedFiles = new LongAdder();
        private final LongAdder failedFiles = new LongAdder();
        private final LongAdder totalBytes = new LongAdder();
        private final LongAdder migratedBytes = new LongAdder();
        private final Queue<VKFileMigrateResult.Failure> failures = new java.util.concurrent.ConcurrentLinkedQueue<>();

        private void incTotalFiles() {
            totalFiles.increment();
        }

        private void incTotalDirs() {
            totalDirs.increment();
        }

        private void incMigratedFiles() {
            migratedFiles.increment();
        }

        private void incSkippedFiles() {
            skippedFiles.increment();
        }

        private void incFailedFiles() {
            failedFiles.increment();
        }

        private void addTotalBytes(long bytes) {
            totalBytes.add(bytes);
        }

        private void addMigratedBytes(long bytes) {
            migratedBytes.add(bytes);
        }

        private void addFailure(String path, String message) {
            failures.add(new VKFileMigrateResult.Failure(path, message));
        }

        private List<VKFileMigrateResult.Failure> drainFailures() {
            return List.copyOf(failures);
        }

        private long totalFiles() {
            return totalFiles.sum();
        }

        private long totalDirs() {
            return totalDirs.sum();
        }

        private long migratedFiles() {
            return migratedFiles.sum();
        }

        private long skippedFiles() {
            return skippedFiles.sum();
        }

        private long failedFiles() {
            return failedFiles.sum();
        }

        private long totalBytes() {
            return totalBytes.sum();
        }

        private long migratedBytes() {
            return migratedBytes.sum();
        }
    }

    private static final class MigrateTask {
        private static final MigrateTask POISON = new MigrateTask(null, null, null, 0L, true);

        private final Path source;
        private final Path target;
        private final String relPath;
        private final long size;
        private final boolean poison;

        private MigrateTask(Path source, Path target, String relPath, long size, boolean poison) {
            this.source = source;
            this.target = target;
            this.relPath = relPath;
            this.size = size;
            this.poison = poison;
        }
    }

    private static final class CheckpointState {
        private final boolean enabled;
        private final Path path;
        private final Set<String> completed;
        // Perf 5：细粒度锁，仅在 checkpointAppend 写入时加锁，不阻塞整个 LocalFileStore 实例
        final Object writeLock = new Object();

        private CheckpointState(boolean enabled, Path path, Set<String> completed) {
            this.enabled = enabled;
            this.path = path;
            this.completed = completed;
        }

        private static CheckpointState disabled() {
            return new CheckpointState(false, null, Set.of());
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private void requireNotNull(Object value, String message) {
        if (value == null) {
            throw arg(message);
        }
    }

    private void requireNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw arg(message);
        }
    }

    private void requireExists(Path path, String rawPath) {
        if (!Files.exists(path)) {
            throw new VKFileException(VKFileErrorCode.NOT_FOUND, "Path does not exist: " + rawPath);
        }
    }

    private void requireFile(Path path, String rawPath) {
        requireExists(path, rawPath);
        if (!Files.isRegularFile(path)) {
            throw arg("Path is not a regular file: " + rawPath);
        }
    }

    private VKFileException arg(String message) {
        return new VKFileException(VKFileErrorCode.INVALID_ARGUMENT, message);
    }

    private VKFileException state(String message) {
        return new VKFileException(VKFileErrorCode.STATE_ERROR, message);
    }

    private VKFileException io(String message, IOException e) {
        return new VKFileException(VKFileErrorCode.IO_ERROR, message, e);
    }
}
