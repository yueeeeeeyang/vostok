package yueyang.vostok.config.loader;

import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VKConfigSource {
    private final String sourceId;
    private final String fileName;
    private final String namespace;
    private final Path file;
    private final byte[] bytes;

    private VKConfigSource(String sourceId, String fileName, String namespace, Path file, byte[] bytes) {
        this.sourceId = sourceId;
        this.fileName = fileName;
        this.namespace = namespace;
        this.file = file;
        this.bytes = bytes;
    }

    public static VKConfigSource ofFile(Path file) {
        String fileName = file.getFileName().toString();
        return new VKConfigSource(file.toAbsolutePath().normalize().toString(), fileName, namespaceOf(fileName), file, null);
    }

    public static VKConfigSource ofBytes(String sourceId, String fileName, byte[] bytes) {
        return new VKConfigSource(sourceId, fileName, namespaceOf(fileName), null, bytes);
    }

    public String sourceId() {
        return sourceId;
    }

    public String fileName() {
        return fileName;
    }

    public String namespace() {
        return namespace;
    }

    public Path file() {
        return file;
    }

    public InputStream openStream() {
        try {
            if (file != null) {
                return Files.newInputStream(file);
            }
            return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
        } catch (IOException e) {
            throw new VKConfigException(VKConfigErrorCode.IO_ERROR, "Failed to open config source: " + sourceId, e);
        }
    }

    private static String namespaceOf(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i <= 0) {
            return fileName;
        }
        return fileName.substring(0, i);
    }
}
