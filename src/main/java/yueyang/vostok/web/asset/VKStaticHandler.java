package yueyang.vostok.web.asset;

import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ConcurrentHashMap;

public final class VKStaticHandler implements VKHandler {
    private static final long SMALL_FILE_LIMIT = 64L * 1024L;

    private final Path baseDir;
    private final String urlPrefix;
    private final ConcurrentHashMap<Path, CacheEntry> cache = new ConcurrentHashMap<>();

    public VKStaticHandler(String urlPrefix, Path baseDir) {
        this.urlPrefix = normalizePrefix(urlPrefix);
        this.baseDir = baseDir.toAbsolutePath().normalize();
    }

    @Override
    public void handle(VKRequest req, VKResponse res) {
        String path = req.path();
        if (!path.startsWith(urlPrefix)) {
            res.status(404).text("Not Found");
            return;
        }

        String rel = path.substring(urlPrefix.length());
        if (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        if (rel.isEmpty()) {
            rel = "index.html";
        }

        Path target = baseDir.resolve(rel).normalize();
        if (!target.startsWith(baseDir)) {
            res.status(403).text("Forbidden");
            return;
        }

        if (Files.isDirectory(target)) {
            target = target.resolve("index.html");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            res.status(404).text("Not Found");
            return;
        }

        try {
            CacheEntry meta = loadMeta(target);
            res.header("ETag", meta.etag);
            if (meta.contentType != null && !meta.contentType.isEmpty()) {
                res.header("Content-Type", meta.contentType);
            }

            String inm = req.header("if-none-match");
            if (inm != null && inm.equals(meta.etag)) {
                res.status(304).body(new byte[0]);
                return;
            }

            if (meta.size <= SMALL_FILE_LIMIT) {
                res.body(Files.readAllBytes(target));
            } else {
                res.file(target, meta.size);
            }
        } catch (Exception e) {
            res.status(500).text("Internal Server Error");
        }
    }

    private CacheEntry loadMeta(Path target) throws Exception {
        FileTime mtime = Files.getLastModifiedTime(target);
        long size = Files.size(target);

        CacheEntry cached = cache.get(target);
        if (cached != null && cached.mtimeMs == mtime.toMillis() && cached.size == size) {
            return cached;
        }

        String ct = cached == null ? detectContentType(target) : cached.contentType;
        if (ct == null || ct.isEmpty()) {
            ct = detectContentType(target);
        }
        String etag = buildEtag(mtime.toMillis(), size);

        CacheEntry fresh = new CacheEntry(mtime.toMillis(), size, ct, etag);
        cache.put(target, fresh);
        return fresh;
    }

    private String buildEtag(long mtimeMs, long size) {
        return "\"" + Long.toHexString(mtimeMs) + '-' + Long.toHexString(size) + "\"";
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "/";
        }
        String p = prefix.startsWith("/") ? prefix : "/" + prefix;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private String detectContentType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class CacheEntry {
        final long mtimeMs;
        final long size;
        final String contentType;
        final String etag;

        CacheEntry(long mtimeMs, long size, String contentType, String etag) {
            this.mtimeMs = mtimeMs;
            this.size = size;
            this.contentType = contentType;
            this.etag = etag;
        }
    }
}
