package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.ppt.VKPptDocument;
import yueyang.vostok.office.ppt.VKPptImage;
import yueyang.vostok.office.ppt.VKPptImageLoadMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 读取解包后的 pptx 目录。 */
public final class VKPptPackageReader {
    private final Path packageRoot;
    private final VKPptLimits limits;

    public VKPptPackageReader(Path packageRoot, VKPptLimits limits) {
        this.packageRoot = packageRoot;
        this.limits = limits;
    }

    public String readText() {
        return scan(true, false, false).text();
    }

    public int countChars() {
        return scan(false, false, true).charCount();
    }

    public List<VKPptImage> readImages() {
        boolean loadBytes = limits.imageLoadMode() != VKPptImageLoadMode.METADATA_ONLY;
        return scan(false, true, false, loadBytes).images();
    }

    public int countImages() {
        return scan(false, true, false, false).images().size();
    }

    public int countSlides() {
        return scan(false, false, false).slideCount();
    }

    public VKPptDocument readDocument() {
        boolean loadBytes = limits.imageLoadMode() != VKPptImageLoadMode.METADATA_ONLY;
        ReadResult result = scan(true, true, false, loadBytes);
        return new VKPptDocument(result.text(), result.charCount(), result.slideCount(), result.images());
    }

    private ReadResult scan(boolean collectText, boolean collectImages, boolean countOnlyChars) {
        boolean loadBytes = limits.imageLoadMode() != VKPptImageLoadMode.METADATA_ONLY;
        return scan(collectText, collectImages, countOnlyChars, loadBytes);
    }

    private ReadResult scan(boolean collectText,
                            boolean collectImages,
                            boolean countOnlyChars,
                            boolean loadImageBytes) {
        ensureContentTypesExists();

        VKPptPartGraphResolver.VKPptPartGraph graph = VKPptPartGraphResolver.resolve(packageRoot, limits);

        VKPptTextAccumulator textAccumulator = collectText ? new VKPptTextAccumulator(limits.maxTextChars()) : null;
        VKPptCharCounter charCounter = (!collectText && countOnlyChars)
                ? new VKPptCharCounter(limits.maxTextChars()) : null;

        List<VKPptImageResolver.ImageRef> imageRefs = collectImages ? new ArrayList<>() : List.of();

        for (String partName : graph.slidePartNames()) {
            Path partPath = resolvePartPath(partName);
            VKPptSecurityGuard.assertSafeXmlSample(partPath, limits.xxeSampleBytes());

            VKPptXmlScanner.scan(partPath, new VKPptXmlScanner.NoopSink() {
                @Override
                public void onText(String text) {
                    if (textAccumulator != null) {
                        textAccumulator.onText(text);
                    }
                    if (charCounter != null) {
                        charCounter.onText(text);
                    }
                }

                @Override
                public void onParagraphEnd() {
                    if (textAccumulator != null) {
                        textAccumulator.onParagraphEnd();
                    }
                }

                @Override
                public void onImageRef(String relationId) {
                    if (collectImages) {
                        imageRefs.add(new VKPptImageResolver.ImageRef(partName, relationId));
                    }
                }
            });

            if (textAccumulator != null) {
                textAccumulator.onSlideEnd();
            }
        }

        List<VKPptImage> images = List.of();
        if (collectImages) {
            VKPptImageResolver resolver = new VKPptImageResolver(packageRoot, limits);
            images = resolver.resolve(imageRefs, graph, loadImageBytes);
        }

        String text = textAccumulator == null ? "" : textAccumulator.text();
        int charCount;
        if (textAccumulator != null) {
            charCount = textAccumulator.charCount();
        } else if (charCounter != null) {
            charCount = charCounter.charCount();
        } else {
            charCount = 0;
        }

        return new ReadResult(text, charCount, graph.slidePartNames().size(), images);
    }

    private void ensureContentTypesExists() {
        Path contentTypes = packageRoot.resolve("[Content_Types].xml").normalize();
        if (!Files.exists(contentTypes) || !Files.isRegularFile(contentTypes)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Invalid pptx package: missing [Content_Types].xml");
        }
    }

    private Path resolvePartPath(String partName) {
        Path p = packageRoot.resolve(partName).normalize();
        if (!p.startsWith(packageRoot.normalize())) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PPT part escapes package root: " + partName);
        }
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "PPT part file not found: " + partName);
        }
        return p;
    }

    private record ReadResult(String text, int charCount, int slideCount, List<VKPptImage> images) {
    }
}
