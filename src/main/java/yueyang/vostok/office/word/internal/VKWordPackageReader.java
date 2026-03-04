package yueyang.vostok.office.word.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.word.VKWordDocument;
import yueyang.vostok.office.word.VKWordImage;
import yueyang.vostok.office.word.VKWordImageLoadMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 读取解包后的 docx 目录。 */
public final class VKWordPackageReader {
    private final Path packageRoot;
    private final VKWordLimits limits;

    public VKWordPackageReader(Path packageRoot, VKWordLimits limits) {
        this.packageRoot = packageRoot;
        this.limits = limits;
    }

    public String readText() {
        return scan(true, false, false).text();
    }

    public int countChars() {
        return scan(false, false, true).charCount();
    }

    public List<VKWordImage> readImages() {
        boolean loadBytes = limits.imageLoadMode() != VKWordImageLoadMode.METADATA_ONLY;
        return scan(false, true, false, loadBytes).images();
    }

    public int countImages() {
        return scan(false, true, false, false).images().size();
    }

    public VKWordDocument readDocument() {
        boolean loadBytes = limits.imageLoadMode() != VKWordImageLoadMode.METADATA_ONLY;
        ReadResult result = scan(true, true, false, loadBytes);
        return new VKWordDocument(result.text(), result.charCount(), result.images());
    }

    private ReadResult scan(boolean collectText, boolean collectImages, boolean countOnlyChars) {
        boolean loadBytes = limits.imageLoadMode() != VKWordImageLoadMode.METADATA_ONLY;
        return scan(collectText, collectImages, countOnlyChars, loadBytes);
    }

    private ReadResult scan(boolean collectText,
                            boolean collectImages,
                            boolean countOnlyChars,
                            boolean loadImageBytes) {
        ensureContentTypesExists();

        VKWordPartGraphResolver.VKWordPartGraph graph = VKWordPartGraphResolver.resolve(packageRoot, limits);

        VKWordTextAccumulator textAccumulator = collectText ? new VKWordTextAccumulator(limits.maxTextChars()) : null;
        VKWordCharCounter charCounter = (!collectText && countOnlyChars)
                ? new VKWordCharCounter(limits.maxTextChars()) : null;

        List<VKWordImageResolver.ImageRef> imageRefs = collectImages ? new ArrayList<>() : List.of();

        for (String partName : graph.scanPartNames()) {
            Path partPath = resolvePartPath(partName);
            VKWordSecurityGuard.assertSafeXmlSample(partPath, limits.xxeSampleBytes());

            VKWordXmlScanner.scan(partPath, new VKWordXmlScanner.NoopSink() {
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
                public void onTab() {
                    if (textAccumulator != null) {
                        textAccumulator.onTab();
                    }
                }

                @Override
                public void onBreak() {
                    if (textAccumulator != null) {
                        textAccumulator.onBreak();
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
                        imageRefs.add(new VKWordImageResolver.ImageRef(partName, relationId));
                    }
                }
            });
        }

        List<VKWordImage> images = List.of();
        if (collectImages) {
            VKWordImageResolver resolver = new VKWordImageResolver(packageRoot, limits);
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

        return new ReadResult(text, charCount, images);
    }

    private void ensureContentTypesExists() {
        Path contentTypes = packageRoot.resolve("[Content_Types].xml").normalize();
        if (!Files.exists(contentTypes) || !Files.isRegularFile(contentTypes)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Invalid docx package: missing [Content_Types].xml");
        }
    }

    private Path resolvePartPath(String partName) {
        Path p = packageRoot.resolve(partName).normalize();
        if (!p.startsWith(packageRoot.normalize())) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Word part escapes package root: " + partName);
        }
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Word part file not found: " + partName);
        }
        return p;
    }

    private record ReadResult(String text, int charCount, List<VKWordImage> images) {
    }
}
