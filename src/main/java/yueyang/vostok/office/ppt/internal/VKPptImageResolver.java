package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.ppt.VKPptImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 按 XML 中的引用顺序解析 PPT 图片。 */
public final class VKPptImageResolver {
    private final Path packageRoot;
    private final VKPptLimits limits;

    public VKPptImageResolver(Path packageRoot, VKPptLimits limits) {
        this.packageRoot = packageRoot;
        this.limits = limits;
    }

    public List<VKPptImage> resolve(List<ImageRef> refs,
                                    VKPptPartGraphResolver.VKPptPartGraph graph,
                                    boolean loadBytes) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<VKPptImage> out = new ArrayList<>(refs.size());
        long totalBytes = 0L;

        int index = 0;
        for (ImageRef ref : refs) {
            Map<String, String> rels = graph.relationships(ref.partName());
            String targetPart = rels.get(ref.relationId());
            if (targetPart == null || targetPart.isBlank()) {
                continue;
            }
            Path mediaPath = packageRoot.resolve(targetPart).normalize();
            if (!mediaPath.startsWith(packageRoot.normalize())) {
                throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                        "PPT media path escapes package root: " + targetPart);
            }
            if (!Files.exists(mediaPath) || !Files.isRegularFile(mediaPath)) {
                continue;
            }

            long size;
            try {
                size = Files.size(mediaPath);
            } catch (Exception e) {
                throw new VKOfficeException(VKOfficeErrorCode.IO_ERROR,
                        "Read ppt media size failed: " + mediaPath, e);
            }
            checkSingleImageBytes(size);
            totalBytes += size;
            checkTotalImageBytes(totalBytes);

            byte[] bytes = null;
            if (loadBytes) {
                try {
                    bytes = Files.readAllBytes(mediaPath);
                } catch (Exception e) {
                    throw new VKOfficeException(VKOfficeErrorCode.IO_ERROR,
                            "Read ppt media bytes failed: " + mediaPath, e);
                }
            }

            index++;
            checkImageCount(index);
            String mediaPart = packageRoot.normalize().relativize(mediaPath).toString().replace('\\', '/');
            out.add(new VKPptImage(index,
                    graph.slideIndex(ref.partName()),
                    ref.partName(),
                    mediaPart,
                    VKPptContentTypeResolver.contentTypeByFileName(mediaPath.getFileName().toString()),
                    size,
                    bytes));
        }

        return out;
    }

    private void checkImageCount(int count) {
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

    /** 图片引用（来自某个 slide part 的关系 ID）。 */
    public record ImageRef(String partName, String relationId) {
    }
}
