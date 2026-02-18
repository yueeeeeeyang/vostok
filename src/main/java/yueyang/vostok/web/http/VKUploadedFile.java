package yueyang.vostok.web.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VKUploadedFile {
    private final String fieldName;
    private final String fileName;
    private final String contentType;
    private final long size;
    private final byte[] inMemory;
    private final Path tempFile;

    VKUploadedFile(String fieldName, String fileName, String contentType, byte[] inMemory, Path tempFile, long size) {
        this.fieldName = fieldName;
        this.fileName = fileName;
        this.contentType = contentType;
        this.inMemory = inMemory;
        this.tempFile = tempFile;
        this.size = size;
    }

    public String fieldName() {
        return fieldName;
    }

    public String fileName() {
        return fileName;
    }

    public String contentType() {
        return contentType;
    }

    public long size() {
        return size;
    }

    public boolean inMemory() {
        return inMemory != null;
    }

    public InputStream inputStream() {
        if (inMemory != null) {
            return new ByteArrayInputStream(inMemory);
        }
        try {
            return Files.newInputStream(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] bytes() {
        if (inMemory != null) {
            return inMemory.clone();
        }
        try {
            return Files.readAllBytes(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void transferTo(Path target) {
        try {
            if (inMemory != null) {
                Files.write(target, inMemory);
            } else {
                Files.copy(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void cleanup() {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignore) {
            }
        }
    }
}
