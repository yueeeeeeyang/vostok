package yueyang.vostok.web.asset;

import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

import java.nio.file.Files;
import java.nio.file.Path;

public final class VKStaticHandler implements VKHandler {
    private final Path baseDir;
    private final String urlPrefix;

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
            long size = Files.size(target);
            String ct = contentType(target);
            if (ct != null) {
                res.header("Content-Type", ct);
            }
            if (size <= 64 * 1024) {
                res.body(Files.readAllBytes(target));
            } else {
                res.file(target, size);
            }
        } catch (Exception e) {
            res.status(500).text("Internal Server Error");
        }
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "/";
        }
        String p = prefix.startsWith("/") ? prefix : "/" + prefix;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private String contentType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (Exception e) {
            return null;
        }
    }
}
