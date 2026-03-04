package yueyang.vostok.office.word.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.word.VKWordImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 按 XML 中的引用顺序解析 Word 图片。 */
public final class VKWordImageResolver {
    private final Path packageRoot;
    private final VKWordLimits limits;

    public VKWordImageResolver(Path packageRoot, VKWordLimits limits) {
        this.packageRoot = packageRoot;
        this.limits = limits;
    }

    public List<VKWordImage> resolve(List<ImageRef> refs,
                                     VKWordPartGraphResolver.VKWordPartGraph graph,
                                     boolean loadBytes) {
        List<VKWordImage> images = new ArrayList<>();
        if (refs == null || refs.isEmpty()) {
            return images;
        }

        long totalImageBytes = 0L;
        int index = 0;
        for (ImageRef ref : refs) {
            Map<String, String> rels = graph.relationships(ref.partName());
            String targetPart = rels.get(ref.relationId());
            if (targetPart == null || targetPart.isBlank()) {
                throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                        "Image relationship not found: " + ref.partName() + "#" + ref.relationId());
            }

            Path mediaPath = packageRoot.resolve(targetPart).normalize();
            if (!mediaPath.startsWith(packageRoot.normalize())) {
                throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                        "Word image path escapes package root: " + targetPart);
            }
            if (!Files.exists(mediaPath) || !Files.isRegularFile(mediaPath)) {
                throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                        "Word image file not found: " + targetPart);
            }

            long imageSize;
            try {
                imageSize = Files.size(mediaPath);
            } catch (Exception e) {
                throw new VKOfficeException(VKOfficeErrorCode.IO_ERROR,
                        "Read word image size failed: " + targetPart, e);
            }

            checkLimitSingleImage(imageSize);
            totalImageBytes += imageSize;
            checkLimitTotalImageBytes(totalImageBytes);

            index++;
            checkLimitImageCount(index);

            byte[] bytes = null;
            if (loadBytes) {
                try {
                    bytes = Files.readAllBytes(mediaPath);
                } catch (Exception e) {
                    throw new VKOfficeException(VKOfficeErrorCode.IO_ERROR,
                            "Read word image bytes failed: " + targetPart, e);
                }
            }

            images.add(new VKWordImage(
                    index,
                    ref.partName(),
                    targetPart,
                    VKWordContentTypeResolver.contentTypeByFileName(mediaPath.getFileName().toString()),
                    imageSize,
                    bytes
            ));
        }
        return images;
    }

    private void checkLimitSingleImage(long size) {
        if (limits.maxSingleImageBytes() > 0 && size > limits.maxSingleImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word single image bytes exceed limit: " + size + " > " + limits.maxSingleImageBytes());
        }
    }

    private void checkLimitTotalImageBytes(long total) {
        if (limits.maxTotalImageBytes() > 0 && total > limits.maxTotalImageBytes()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word total image bytes exceed limit: " + total + " > " + limits.maxTotalImageBytes());
        }
    }

    private void checkLimitImageCount(int count) {
        if (limits.maxImages() > 0 && count > limits.maxImages()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word images exceed limit: " + count + " > " + limits.maxImages());
        }
    }

    /** 图片引用（来自某个 part 的关系 ID）。 */
    public record ImageRef(String partName, String relationId) {
    }
}
