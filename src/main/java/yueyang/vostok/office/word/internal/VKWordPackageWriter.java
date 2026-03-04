package yueyang.vostok.office.word.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.word.VKWordImageElement;
import yueyang.vostok.office.word.VKWordImageSourceType;
import yueyang.vostok.office.word.VKWordParagraphElement;
import yueyang.vostok.office.word.VKWordWriteElement;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 写入解包形态的 docx 目录。 */
public final class VKWordPackageWriter {
    private final Path packageRoot;
    private final Path fileBaseDir;
    private final VKWordLimits limits;

    public VKWordPackageWriter(Path packageRoot, Path fileBaseDir, VKWordLimits limits) {
        this.packageRoot = packageRoot;
        this.fileBaseDir = fileBaseDir;
        this.limits = limits;
    }

    public void writePackage(VKWordWriteRequest request) {
        if (request == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Word write request is null");
        }

        List<VKWordWriteElement> elements = request.elements();
        List<VKWordXmlWriter.BodyBlock> blocks = new ArrayList<>();
        Map<String, String> imageRels = new LinkedHashMap<>();
        Set<String> imageExtensions = new LinkedHashSet<>();

        int textChars = 0;
        int imageCount = 0;
        long totalImageBytes = 0L;

        VKWordMediaWriter mediaWriter = new VKWordMediaWriter(packageRoot, fileBaseDir);

        int imageIndex = 0;
        for (VKWordWriteElement element : elements) {
            if (element instanceof VKWordParagraphElement paragraph) {
                String text = paragraph.text();
                textChars += VKWordCharCounter.countNonWhitespace(text);
                checkTextChars(textChars);
                blocks.add(VKWordXmlWriter.BodyBlock.paragraph(text));
                continue;
            }

            if (element instanceof VKWordImageElement image) {
                imageCount++;
                checkImageCount(imageCount);
                imageIndex++;

                VKWordMediaWriter.PreparedImage prepared = writeImage(mediaWriter, imageIndex, image);
                checkSingleImageBytes(prepared.size());
                totalImageBytes += prepared.size();
                checkTotalImageBytes(totalImageBytes);

                String rid = "rIdImg" + imageIndex;
                String relTarget = toRelTargetForDocumentRels(prepared.mediaPart());
                imageRels.put(rid, relTarget);
                imageExtensions.add(prepared.extension());

                blocks.add(VKWordXmlWriter.BodyBlock.image(rid, "image" + imageIndex));
                continue;
            }

            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT,
                    "Unsupported word write element: " + (element == null ? "null" : element.getClass().getName()));
        }

        if (blocks.isEmpty()) {
            blocks.add(VKWordXmlWriter.BodyBlock.paragraph(""));
        }

        VKWordXmlWriter.writePackage(packageRoot, blocks, imageRels, imageExtensions);
    }

    private VKWordMediaWriter.PreparedImage writeImage(VKWordMediaWriter mediaWriter,
                                                        int imageIndex,
                                                        VKWordImageElement image) {
        VKWordImageSourceType sourceType = image.sourceType();
        if (sourceType == VKWordImageSourceType.BYTES) {
            return mediaWriter.writeBytes(imageIndex, image.fileName(), image.bytes());
        }
        if (sourceType == VKWordImageSourceType.FILE_PATH) {
            return mediaWriter.writeFile(imageIndex, image.filePath());
        }
        throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT,
                "Unsupported word image source type: " + sourceType);
    }

    private String toRelTargetForDocumentRels(String mediaPart) {
        // document.xml 位于 word/，因此 rel target 需相对到 media/xxx
        String v = mediaPart == null ? "" : mediaPart.replace('\\', '/');
        if (v.startsWith("word/")) {
            return v.substring("word/".length());
        }
        return v;
    }

    private void checkTextChars(int count) {
        if (limits.maxTextChars() > 0 && count > limits.maxTextChars()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word chars exceed limit: " + count + " > " + limits.maxTextChars());
        }
    }

    private void checkImageCount(int count) {
        if (limits.maxImages() > 0 && count > limits.maxImages()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word images exceed limit: " + count + " > " + limits.maxImages());
        }
    }

    private void checkSingleImageBytes(long size) {
        if (limits.maxSingleImageBytes() > 0 && size > limits.maxSingleImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word single image bytes exceed limit: " + size + " > " + limits.maxSingleImageBytes());
        }
    }

    private void checkTotalImageBytes(long total) {
        if (limits.maxTotalImageBytes() > 0 && total > limits.maxTotalImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word total image bytes exceed limit: " + total + " > " + limits.maxTotalImageBytes());
        }
    }
}
