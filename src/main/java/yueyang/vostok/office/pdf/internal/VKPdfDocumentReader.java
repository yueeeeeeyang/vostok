package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.pdf.VKPdfDocument;
import yueyang.vostok.office.pdf.VKPdfImage;
import yueyang.vostok.office.pdf.VKPdfImageLoadMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读取 PDF 文档（文本/图片/计数聚合）。 */
public final class VKPdfDocumentReader {
    private static final Pattern REF_PATTERN = Pattern.compile("(\\d+)\\s+0\\s+R");
    private static final Pattern PAGES_REF_PATTERN = Pattern.compile("/Pages\\s+(\\d+)\\s+0\\s+R");
    private static final Pattern KIDS_PATTERN = Pattern.compile("/Kids\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern CONTENTS_REF_PATTERN = Pattern.compile("/Contents\\s+(\\d+)\\s+0\\s+R");
    private static final Pattern CONTENTS_ARRAY_PATTERN = Pattern.compile("/Contents\\s*\\[(.*?)]", Pattern.DOTALL);

    private final VKPdfLimits limits;
    private final Map<Integer, VKPdfObjectReader.PdfObject> objects;
    private final List<Integer> pageObjectIds;

    public VKPdfDocumentReader(byte[] pdfBytes, VKPdfLimits limits) {
        this.limits = limits;
        assertPdfHeader(pdfBytes);
        // 解析 xref，结果用于快速诊断；对象扫描仍以 obj/endobj 为准。
        VKPdfXrefReader.readOffsets(pdfBytes);
        this.objects = VKPdfObjectReader.readObjects(pdfBytes, limits.maxObjects());
        this.pageObjectIds = resolvePageObjectIds(objects);
        checkPages(pageObjectIds.size());
    }

    public String readText() {
        return scan(true, false, false).text();
    }

    public int countChars() {
        return scan(false, false, true).charCount();
    }

    public List<VKPdfImage> readImages() {
        boolean loadBytes = limits.imageLoadMode() != VKPdfImageLoadMode.METADATA_ONLY;
        return scan(false, true, false, loadBytes).images();
    }

    public int countImages() {
        return scan(false, true, false, false).images().size();
    }

    public int countPages() {
        return pageObjectIds.size();
    }

    public VKPdfDocument readDocument() {
        boolean loadBytes = limits.imageLoadMode() != VKPdfImageLoadMode.METADATA_ONLY;
        ScanResult result = scan(true, true, false, loadBytes);
        return new VKPdfDocument(result.text(), result.charCount(), pageObjectIds.size(), result.images());
    }

    private ScanResult scan(boolean collectText, boolean collectImages, boolean countOnlyChars) {
        boolean loadBytes = limits.imageLoadMode() != VKPdfImageLoadMode.METADATA_ONLY;
        return scan(collectText, collectImages, countOnlyChars, loadBytes);
    }

    private ScanResult scan(boolean collectText,
                            boolean collectImages,
                            boolean countOnlyChars,
                            boolean loadImageBytes) {
        StringBuilder text = collectText ? new StringBuilder() : null;
        int charCount = 0;

        List<VKPdfImage> images = collectImages ? new ArrayList<>() : List.of();
        VKPdfImageResolver imageResolver = collectImages ? new VKPdfImageResolver(limits) : null;
        long[] totalImageBytes = collectImages ? new long[]{0L} : null;

        for (int i = 0; i < pageObjectIds.size(); i++) {
            int pageIndex = i + 1;
            VKPdfObjectReader.PdfObject pageObj = objects.get(pageObjectIds.get(i));
            if (pageObj == null) {
                continue;
            }
            String pageText = readPageText(pageObj.dictionary());
            if (collectText) {
                text.append(pageText);
                if (!pageText.isEmpty() && text.charAt(text.length() - 1) != '\n') {
                    text.append('\n');
                }
            }
            if (collectText || countOnlyChars) {
                charCount += VKPdfTextScanner.countNonWhitespace(pageText);
                checkTextChars(charCount);
            }

            if (collectImages) {
                List<VKPdfImage> pageImages = imageResolver.resolveForPage(
                        pageIndex,
                        pageObj.dictionary(),
                        objects,
                        loadImageBytes,
                        images.size(),
                        totalImageBytes);
                images.addAll(pageImages);
            }
        }

        return new ScanResult(text == null ? "" : text.toString(), charCount, images);
    }

    private String readPageText(String pageDict) {
        List<Integer> contentObjIds = resolveContentObjectIds(pageDict);
        if (contentObjIds.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Integer objId : contentObjIds) {
            VKPdfObjectReader.PdfObject contentObj = objects.get(objId);
            if (contentObj == null || contentObj.streamBytes() == null) {
                continue;
            }
            byte[] stream = VKPdfStreamDecoder.decode(contentObj.dictionary(), contentObj.streamBytes(), limits.maxStreamBytes());
            String pageText = VKPdfTextScanner.extractText(stream);
            out.append(pageText);
        }
        return out.toString();
    }

    private List<Integer> resolveContentObjectIds(String pageDict) {
        if (pageDict == null || pageDict.isBlank()) {
            return List.of();
        }
        Matcher arr = CONTENTS_ARRAY_PATTERN.matcher(pageDict);
        if (arr.find()) {
            return parseRefList(arr.group(1));
        }
        Matcher one = CONTENTS_REF_PATTERN.matcher(pageDict);
        if (one.find()) {
            try {
                return List.of(Integer.parseInt(one.group(1)));
            } catch (Exception ignore) {
            }
        }
        return List.of();
    }

    private List<Integer> resolvePageObjectIds(Map<Integer, VKPdfObjectReader.PdfObject> objects) {
        int rootPagesId = resolveRootPagesId(objects);
        if (rootPagesId > 0) {
            List<Integer> pageIds = new ArrayList<>();
            collectPageObjects(rootPagesId, pageIds);
            if (!pageIds.isEmpty()) {
                return pageIds;
            }
        }

        // 回退：扫描所有 /Type /Page 对象。
        List<Integer> fallback = new ArrayList<>();
        for (Map.Entry<Integer, VKPdfObjectReader.PdfObject> e : objects.entrySet()) {
            String lower = safeLower(e.getValue().dictionary());
            if (lower.contains("/type /page") && !lower.contains("/type /pages")) {
                fallback.add(e.getKey());
            }
        }
        if (fallback.isEmpty()) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "PDF pages not found");
        }
        return fallback;
    }

    private int resolveRootPagesId(Map<Integer, VKPdfObjectReader.PdfObject> objects) {
        for (VKPdfObjectReader.PdfObject obj : objects.values()) {
            String lower = safeLower(obj.dictionary());
            if (!lower.contains("/type /catalog")) {
                continue;
            }
            Matcher m = PAGES_REF_PATTERN.matcher(obj.dictionary());
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (Exception ignore) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private void collectPageObjects(int objId, List<Integer> out) {
        VKPdfObjectReader.PdfObject obj = objects.get(objId);
        if (obj == null) {
            return;
        }
        String lower = safeLower(obj.dictionary());
        if (lower.contains("/type /page") && !lower.contains("/type /pages")) {
            out.add(objId);
            return;
        }

        Matcher kids = KIDS_PATTERN.matcher(obj.dictionary());
        if (!kids.find()) {
            return;
        }
        List<Integer> refs = parseRefList(kids.group(1));
        for (Integer ref : refs) {
            collectPageObjects(ref, out);
        }
    }

    private static List<Integer> parseRefList(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        Matcher m = REF_PATTERN.matcher(source);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group(1)));
            } catch (Exception ignore) {
            }
        }
        return out;
    }

    private static void assertPdfHeader(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 5) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "PDF bytes is empty");
        }
        String head = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.ISO_8859_1);
        if (!head.startsWith("%PDF-")) {
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                    "Unsupported PDF header");
        }
    }

    private static String safeLower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private void checkTextChars(int count) {
        if (limits.maxTextChars() > 0 && count > limits.maxTextChars()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF chars exceed limit: " + count + " > " + limits.maxTextChars());
        }
    }

    private void checkPages(int count) {
        if (limits.maxPages() > 0 && count > limits.maxPages()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PDF pages exceed limit: " + count + " > " + limits.maxPages());
        }
    }

    private record ScanResult(String text, int charCount, List<VKPdfImage> images) {
    }
}
