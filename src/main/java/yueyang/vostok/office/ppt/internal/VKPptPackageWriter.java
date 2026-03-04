package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.ppt.VKPptImageElement;
import yueyang.vostok.office.ppt.VKPptImageSourceType;
import yueyang.vostok.office.ppt.VKPptParagraphElement;
import yueyang.vostok.office.ppt.VKPptWriteElement;
import yueyang.vostok.office.ppt.VKPptWriteRequest;
import yueyang.vostok.office.ppt.VKPptWriteSlide;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 写入解包形态的 pptx 目录。 */
public final class VKPptPackageWriter {
    private final Path packageRoot;
    private final Path fileBaseDir;
    private final VKPptLimits limits;

    public VKPptPackageWriter(Path packageRoot, Path fileBaseDir, VKPptLimits limits) {
        this.packageRoot = packageRoot;
        this.fileBaseDir = fileBaseDir;
        this.limits = limits;
    }

    public void writePackage(VKPptWriteRequest request) {
        if (request == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PPT write request is null");
        }

        List<VKPptWriteSlide> slides = request.slides();
        if (slides.isEmpty()) {
            slides = List.of(new VKPptWriteSlide().addParagraph(""));
        }
        checkSlides(slides.size());

        List<VKPptXmlWriter.SlidePart> slideParts = new ArrayList<>();
        Set<String> imageExtensions = new LinkedHashSet<>();

        int totalChars = 0;
        int totalImages = 0;
        long totalImageBytes = 0L;
        int imageIndex = 0;

        VKPptMediaWriter mediaWriter = new VKPptMediaWriter(packageRoot, fileBaseDir);

        for (int i = 0; i < slides.size(); i++) {
            VKPptWriteSlide slide = slides.get(i);
            List<VKPptWriteElement> elements = slide.elements();
            List<VKPptXmlWriter.BodyBlock> blocks = new ArrayList<>();
            Map<String, String> imageRels = new LinkedHashMap<>();
            int slideImageNo = 0;

            for (VKPptWriteElement element : elements) {
                if (element instanceof VKPptParagraphElement paragraph) {
                    String text = paragraph.text();
                    totalChars += VKPptCharCounter.countNonWhitespace(text);
                    checkTextChars(totalChars);
                    blocks.add(VKPptXmlWriter.BodyBlock.paragraph(text, paragraph.style()));
                    continue;
                }

                if (element instanceof VKPptImageElement image) {
                    totalImages++;
                    checkImages(totalImages);
                    imageIndex++;
                    slideImageNo++;

                    VKPptMediaWriter.PreparedImage prepared = writeImage(mediaWriter, imageIndex, image);
                    checkSingleImageBytes(prepared.size());
                    totalImageBytes += prepared.size();
                    checkTotalImageBytes(totalImageBytes);

                    String rid = "rIdImg" + slideImageNo;
                    String relTarget = toRelTargetForSlideRels(prepared.mediaPart());
                    imageRels.put(rid, relTarget);
                    imageExtensions.add(prepared.extension());

                    blocks.add(VKPptXmlWriter.BodyBlock.image(rid, "image" + imageIndex, image.style()));
                    continue;
                }

                throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT,
                        "Unsupported ppt write element: " + (element == null ? "null" : element.getClass().getName()));
            }

            if (blocks.isEmpty()) {
                blocks.add(VKPptXmlWriter.BodyBlock.paragraph(""));
            }
            slideParts.add(new VKPptXmlWriter.SlidePart(i + 1, blocks, imageRels));
        }

        VKPptXmlWriter.writePackage(packageRoot, slideParts, imageExtensions, request.layoutStyle());
    }

    private VKPptMediaWriter.PreparedImage writeImage(VKPptMediaWriter mediaWriter,
                                                       int imageIndex,
                                                       VKPptImageElement image) {
        VKPptImageSourceType sourceType = image.sourceType();
        if (sourceType == VKPptImageSourceType.BYTES) {
            return mediaWriter.writeBytes(imageIndex, image.fileName(), image.bytes());
        }
        if (sourceType == VKPptImageSourceType.FILE_PATH) {
            return mediaWriter.writeFile(imageIndex, image.filePath());
        }
        throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT,
                "Unsupported ppt image source type: " + sourceType);
    }

    private String toRelTargetForSlideRels(String mediaPart) {
        // slideN.xml 位于 ppt/slides/，关系 target 需相对到 ../media/xxx
        String v = mediaPart == null ? "" : mediaPart.replace('\\', '/');
        if (v.startsWith("ppt/")) {
            v = v.substring("ppt/".length());
        }
        if (v.startsWith("media/")) {
            return "../" + v;
        }
        return v;
    }

    private void checkSlides(int count) {
        if (limits.maxSlides() > 0 && count > limits.maxSlides()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PPT slides exceed limit: " + count + " > " + limits.maxSlides());
        }
    }

    private void checkTextChars(int count) {
        if (limits.maxTextChars() > 0 && count > limits.maxTextChars()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PPT chars exceed limit: " + count + " > " + limits.maxTextChars());
        }
    }

    private void checkImages(int count) {
        if (limits.maxImages() > 0 && count > limits.maxImages()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PPT images exceed limit: " + count + " > " + limits.maxImages());
        }
    }

    private void checkSingleImageBytes(long size) {
        if (limits.maxSingleImageBytes() > 0 && size > limits.maxSingleImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PPT single image bytes exceed limit: " + size + " > " + limits.maxSingleImageBytes());
        }
    }

    private void checkTotalImageBytes(long total) {
        if (limits.maxTotalImageBytes() > 0 && total > limits.maxTotalImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PPT total image bytes exceed limit: " + total + " > " + limits.maxTotalImageBytes());
        }
    }
}
