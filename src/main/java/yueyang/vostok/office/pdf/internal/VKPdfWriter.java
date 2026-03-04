package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.pdf.VKPdfImageElement;
import yueyang.vostok.office.pdf.VKPdfImageSourceType;
import yueyang.vostok.office.pdf.VKPdfParagraphElement;
import yueyang.vostok.office.pdf.VKPdfWriteElement;
import yueyang.vostok.office.pdf.VKPdfWritePage;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;
import yueyang.vostok.security.VostokSecurity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 生成最小可读 PDF（文本 + 图片）。 */
public final class VKPdfWriter {
    private final Path baseDir;
    private final VKPdfLimits limits;

    public VKPdfWriter(Path baseDir, VKPdfLimits limits) {
        this.baseDir = baseDir;
        this.limits = limits;
    }

    public byte[] write(VKPdfWriteRequest request) {
        if (request == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PDF write request is null");
        }

        List<VKPdfWritePage> pages = request.pages();
        if (pages.isEmpty()) {
            pages = List.of(new VKPdfWritePage().addParagraph(""));
        }
        checkPages(pages.size());

        List<PageModel> pageModels = new ArrayList<>();
        int totalChars = 0;
        int totalImages = 0;
        long totalImageBytes = 0L;

        for (int i = 0; i < pages.size(); i++) {
            VKPdfWritePage page = pages.get(i);
            List<VKPdfPageComposer.Block> blocks = new ArrayList<>();
            List<PreparedImage> pageImages = new ArrayList<>();

            int imageNo = 0;
            for (VKPdfWriteElement element : page.elements()) {
                if (element instanceof VKPdfParagraphElement paragraph) {
                    String text = paragraph.text();
                    totalChars += VKPdfTextScanner.countNonWhitespace(text);
                    checkTextChars(totalChars);
                    blocks.add(VKPdfPageComposer.Block.text(text));
                    continue;
                }

                if (element instanceof VKPdfImageElement image) {
                    totalImages++;
                    checkImages(totalImages);

                    imageNo++;
                    String imageName = "Im" + imageNo;
                    PreparedImage prepared = prepareImage(image);
                    checkSingleImageBytes(prepared.bytes().length);
                    totalImageBytes += prepared.bytes().length;
                    checkTotalImageBytes(totalImageBytes);

                    pageImages.add(prepared.withName(imageName));
                    blocks.add(VKPdfPageComposer.Block.image(imageName));
                    continue;
                }

                throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT,
                        "Unsupported pdf write element: " + (element == null ? "null" : element.getClass().getName()));
            }

            if (blocks.isEmpty()) {
                blocks.add(VKPdfPageComposer.Block.text(""));
            }
            pageModels.add(new PageModel(blocks, pageImages));
        }

        return buildPdf(pageModels);
    }

    private byte[] buildPdf(List<PageModel> pages) {
        Map<Integer, byte[]> objectBodies = new LinkedHashMap<>();
        List<Integer> pageObjectIds = new ArrayList<>();

        objectBodies.put(1, bytes("<< /Type /Catalog /Pages 2 0 R >>\n"));
        objectBodies.put(3, bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n"));

        int nextId = 4;
        for (PageModel page : pages) {
            Map<String, Integer> imageObjIds = new LinkedHashMap<>();
            Map<String, VKPdfPageComposer.ImageBox> imageBoxes = new LinkedHashMap<>();

            for (PreparedImage image : page.images()) {
                int imageObjId = nextId++;
                imageObjIds.put(image.name(), imageObjId);
                imageBoxes.put(image.name(), new VKPdfPageComposer.ImageBox(image.width(), image.height()));
                objectBodies.put(imageObjId, buildImageObject(image));
            }

            byte[] contentBytes = VKPdfPageComposer.compose(page.blocks(), imageBoxes);
            checkStreamBytes(contentBytes.length);
            int contentObjId = nextId++;
            objectBodies.put(contentObjId, buildStreamObject(contentBytes));

            int pageObjId = nextId++;
            pageObjectIds.add(pageObjId);
            objectBodies.put(pageObjId, buildPageObject(contentObjId, imageObjIds));
        }

        objectBodies.put(2, buildPagesObject(pageObjectIds));

        checkObjects(objectBodies.size() + 1);
        byte[] pdf = serializePdf(objectBodies);
        if (limits.maxDocumentBytes() > 0 && pdf.length > limits.maxDocumentBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF document bytes exceed limit: " + pdf.length + " > " + limits.maxDocumentBytes());
        }
        return pdf;
    }

    private byte[] buildPagesObject(List<Integer> pageObjectIds) {
        StringBuilder kids = new StringBuilder();
        for (Integer id : pageObjectIds) {
            kids.append(id).append(" 0 R ");
        }
        return bytes("<< /Type /Pages /Kids [ " + kids + "] /Count " + pageObjectIds.size() + " >>\n");
    }

    private byte[] buildPageObject(int contentObjId, Map<String, Integer> imageObjIds) {
        StringBuilder xobj = new StringBuilder();
        for (Map.Entry<String, Integer> e : imageObjIds.entrySet()) {
            xobj.append('/').append(e.getKey()).append(' ').append(e.getValue()).append(" 0 R ");
        }
        String resources = imageObjIds.isEmpty()
                ? "<< /Font << /F1 3 0 R >> >>"
                : "<< /Font << /F1 3 0 R >> /XObject << " + xobj + ">> >>";

        return bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources "
                + resources + " /Contents " + contentObjId + " 0 R >>\n");
    }

    private byte[] buildImageObject(PreparedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(image.bytes().length + 256);
        String dict = "<< /Type /XObject /Subtype /Image /Width " + image.width()
                + " /Height " + image.height()
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /" + image.filter()
                + " /Length " + image.bytes().length + " >>\n";
        try {
            out.write(dict.getBytes(StandardCharsets.ISO_8859_1));
            out.write("stream\n".getBytes(StandardCharsets.ISO_8859_1));
            out.write(image.bytes());
            out.write("\nendstream\n".getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR,
                    "Build PDF image object failed", e);
        }
    }

    private byte[] buildStreamObject(byte[] stream) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(stream.length + 128);
        String dict = "<< /Length " + stream.length + " >>\n";
        try {
            out.write(dict.getBytes(StandardCharsets.ISO_8859_1));
            out.write("stream\n".getBytes(StandardCharsets.ISO_8859_1));
            out.write(stream);
            out.write("\nendstream\n".getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR,
                    "Build PDF stream object failed", e);
        }
    }

    private byte[] serializePdf(Map<Integer, byte[]> objectBodies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        try {
            out.write(bytes("%PDF-1.4\n"));
            out.write(new byte[]{'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});

            int maxId = objectBodies.keySet().stream().mapToInt(v -> v).max().orElse(0);
            long[] offsets = new long[maxId + 1];

            for (int id = 1; id <= maxId; id++) {
                byte[] body = objectBodies.get(id);
                if (body == null) {
                    continue;
                }
                offsets[id] = out.size();
                out.write(bytes(id + " 0 obj\n"));
                out.write(body);
                if (body.length == 0 || body[body.length - 1] != '\n') {
                    out.write('\n');
                }
                out.write(bytes("endobj\n"));
            }

            long xrefOffset = out.size();
            out.write(bytes("xref\n"));
            out.write(bytes("0 " + (maxId + 1) + "\n"));
            out.write(bytes("0000000000 65535 f \n"));
            for (int id = 1; id <= maxId; id++) {
                long off = offsets[id];
                out.write(bytes(String.format(Locale.ROOT, "%010d 00000 n \n", off)));
            }

            out.write(bytes("trailer\n"));
            out.write(bytes("<< /Size " + (maxId + 1) + " /Root 1 0 R >>\n"));
            out.write(bytes("startxref\n" + xrefOffset + "\n%%EOF\n"));
            return out.toByteArray();
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR,
                    "Serialize PDF failed", e);
        }
    }

    private PreparedImage prepareImage(VKPdfImageElement image) {
        byte[] bytes;
        String name = image.fileName();
        if (image.sourceType() == VKPdfImageSourceType.BYTES) {
            bytes = image.bytes();
            if (bytes == null || bytes.length == 0) {
                throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PDF image bytes is empty");
            }
            if (name == null || name.isBlank()) {
                name = "image.bin";
            }
        } else if (image.sourceType() == VKPdfImageSourceType.FILE_PATH) {
            String filePath = image.filePath();
            if (filePath == null || filePath.isBlank()) {
                throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PDF image file path is blank");
            }
            Path source = resolveImagePath(filePath);
            try {
                bytes = Files.readAllBytes(source);
                name = source.getFileName().toString();
            } catch (Exception e) {
                throw new VKOfficeException(VKOfficeErrorCode.IO_ERROR,
                        "Read PDF image file failed: " + filePath, e);
            }
        } else {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT,
                    "Unsupported pdf image source type: " + image.sourceType());
        }

        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String filter;
        int[] wh = readImageSizeFast(bytes, lower);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            filter = "DCTDecode";
        } else if (lower.endsWith(".png")) {
            filter = "FlateDecode";
        } else {
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                    "Unsupported image format for PDF: " + name);
        }
        return new PreparedImage(null, wh[0], wh[1], filter, bytes);
    }

    private Path resolveImagePath(String filePath) {
        try {
            VostokSecurity.assertSafePath(filePath);
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Unsafe pdf image file path: " + filePath, e);
        }
        Path rel = Path.of(filePath.trim());
        Path resolved = rel.isAbsolute() ? rel.toAbsolutePath().normalize() : baseDir.resolve(rel).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PDF image file path escapes file baseDir: " + filePath);
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new VKOfficeException(VKOfficeErrorCode.NOT_FOUND,
                    "PDF image file not found: " + filePath);
        }
        return resolved;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private void checkPages(int count) {
        if (limits.maxPages() > 0 && count > limits.maxPages()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF pages exceed limit: " + count + " > " + limits.maxPages());
        }
    }

    private void checkTextChars(int count) {
        if (limits.maxTextChars() > 0 && count > limits.maxTextChars()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF chars exceed limit: " + count + " > " + limits.maxTextChars());
        }
    }

    private void checkImages(int count) {
        if (limits.maxImages() > 0 && count > limits.maxImages()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF images exceed limit: " + count + " > " + limits.maxImages());
        }
    }

    private void checkSingleImageBytes(long size) {
        if (limits.maxSingleImageBytes() > 0 && size > limits.maxSingleImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF single image bytes exceed limit: " + size + " > " + limits.maxSingleImageBytes());
        }
    }

    private void checkTotalImageBytes(long total) {
        if (limits.maxTotalImageBytes() > 0 && total > limits.maxTotalImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF total image bytes exceed limit: " + total + " > " + limits.maxTotalImageBytes());
        }
    }

    private void checkObjects(int count) {
        if (limits.maxObjects() > 0 && count > limits.maxObjects()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF objects exceed limit: " + count + " > " + limits.maxObjects());
        }
    }

    private void checkStreamBytes(long size) {
        if (limits.maxStreamBytes() > 0 && size > limits.maxStreamBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF stream bytes exceed limit: " + size + " > " + limits.maxStreamBytes());
        }
    }

    private record PageModel(List<VKPdfPageComposer.Block> blocks, List<PreparedImage> images) {
    }

    /**
     * 快速读取图片尺寸：
     * PNG 解析 IHDR；JPEG 解析 SOF 段；失败时回退默认尺寸。
     */
    private int[] readImageSizeFast(byte[] bytes, String lowerName) {
        if (bytes == null || bytes.length < 24) {
            return new int[]{120, 120};
        }
        if (lowerName.endsWith(".png")
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {
            int w = readInt32(bytes, 16);
            int h = readInt32(bytes, 20);
            return new int[]{Math.max(1, w), Math.max(1, h)};
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            int i = 2;
            while (i + 8 < bytes.length) {
                if ((bytes[i] & 0xFF) != 0xFF) {
                    i++;
                    continue;
                }
                int marker = bytes[i + 1] & 0xFF;
                int len = ((bytes[i + 2] & 0xFF) << 8) | (bytes[i + 3] & 0xFF);
                if (len < 2 || i + 1 + len >= bytes.length) {
                    break;
                }
                if ((marker >= 0xC0 && marker <= 0xC3)
                        || (marker >= 0xC5 && marker <= 0xC7)
                        || (marker >= 0xC9 && marker <= 0xCB)
                        || (marker >= 0xCD && marker <= 0xCF)) {
                    int h = ((bytes[i + 5] & 0xFF) << 8) | (bytes[i + 6] & 0xFF);
                    int w = ((bytes[i + 7] & 0xFF) << 8) | (bytes[i + 8] & 0xFF);
                    return new int[]{Math.max(1, w), Math.max(1, h)};
                }
                i += 2 + len;
            }
        }
        return new int[]{120, 120};
    }

    private int readInt32(byte[] bytes, int offset) {
        if (offset + 3 >= bytes.length) {
            return 120;
        }
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private record PreparedImage(String name, int width, int height, String filter, byte[] bytes) {
        PreparedImage withName(String imageName) {
            return new PreparedImage(imageName, width, height, filter, bytes);
        }
    }
}
