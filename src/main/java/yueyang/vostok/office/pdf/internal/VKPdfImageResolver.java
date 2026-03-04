package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.pdf.VKPdfImage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从 PDF 页面资源中解析图片 XObject。 */
public final class VKPdfImageResolver {
    private static final Pattern REF_PATTERN = Pattern.compile("(\\d+)\\s+0\\s+R");
    private static final Pattern RESOURCES_REF_PATTERN = Pattern.compile("/Resources\\s+(\\d+)\\s+0\\s+R");
    private static final Pattern XOBJECT_ITEM_PATTERN = Pattern.compile("/([A-Za-z0-9_]+)\\s+(\\d+)\\s+0\\s+R");

    private final VKPdfLimits limits;

    public VKPdfImageResolver(VKPdfLimits limits) {
        this.limits = limits;
    }

    public List<VKPdfImage> resolveForPage(int pageIndex,
                                           String pageDictionary,
                                           Map<Integer, VKPdfObjectReader.PdfObject> objects,
                                           boolean loadBytes,
                                           int startIndex,
                                           long[] totalImageBytesHolder) {
        Map<String, Integer> xObjects = resolveXObjectRefs(pageDictionary, objects);
        if (xObjects.isEmpty()) {
            return List.of();
        }

        List<VKPdfImage> out = new ArrayList<>();
        int idx = startIndex;
        long total = totalImageBytesHolder == null || totalImageBytesHolder.length == 0 ? 0L : totalImageBytesHolder[0];

        for (Map.Entry<String, Integer> e : xObjects.entrySet()) {
            VKPdfObjectReader.PdfObject obj = objects.get(e.getValue());
            if (obj == null) {
                continue;
            }
            String dict = safeLower(obj.dictionary());
            if (!dict.contains("/subtype /image")) {
                continue;
            }
            if (dict.contains("/filter")
                    && !dict.contains("/dctdecode")
                    && !dict.contains("/flatedecode")) {
                throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                        "Unsupported PDF image filter");
            }
            byte[] raw = obj.streamBytes();
            if (raw == null) {
                continue;
            }
            long size = raw.length;
            checkSingleImageBytes(size);
            total += size;
            checkTotalImageBytes(total);

            idx++;
            checkImageCount(idx);

            String objectRef = obj.id() + " 0 R";
            String contentType = contentTypeByDict(dict);
            byte[] bytes = loadBytes ? raw : null;
            out.add(new VKPdfImage(idx, pageIndex, objectRef, contentType, size, bytes));
        }

        if (totalImageBytesHolder != null && totalImageBytesHolder.length > 0) {
            totalImageBytesHolder[0] = total;
        }
        return out;
    }

    private Map<String, Integer> resolveXObjectRefs(String pageDictionary,
                                                    Map<Integer, VKPdfObjectReader.PdfObject> objects) {
        String resourceDict = findResourceDict(pageDictionary, objects);
        if (resourceDict == null || resourceDict.isBlank()) {
            return Map.of();
        }
        String xObjBlock = extractInlineDictionary(resourceDict, "/XObject");
        if (xObjBlock.isBlank()) {
            return Map.of();
        }
        Matcher itemMatcher = XOBJECT_ITEM_PATTERN.matcher(xObjBlock);
        Map<String, Integer> out = new LinkedHashMap<>();
        while (itemMatcher.find()) {
            String name = itemMatcher.group(1);
            int objId;
            try {
                objId = Integer.parseInt(itemMatcher.group(2));
            } catch (Exception ex) {
                continue;
            }
            out.put(name, objId);
        }
        return out;
    }

    private String findResourceDict(String pageDictionary,
                                    Map<Integer, VKPdfObjectReader.PdfObject> objects) {
        if (pageDictionary == null || pageDictionary.isBlank()) {
            return "";
        }

        String inline = extractInlineDictionary(pageDictionary, "/Resources");
        if (!inline.isBlank()) {
            return inline;
        }

        Matcher ref = RESOURCES_REF_PATTERN.matcher(pageDictionary);
        if (ref.find()) {
            int id;
            try {
                id = Integer.parseInt(ref.group(1));
            } catch (Exception e) {
                return "";
            }
            VKPdfObjectReader.PdfObject obj = objects.get(id);
            return obj == null ? "" : obj.dictionary();
        }
        return "";
    }

    /**
     * 从 `/Key << ... >>` 结构中提取字典体，支持简单嵌套。
     */
    private String extractInlineDictionary(String source, String keyToken) {
        if (source == null || source.isBlank() || keyToken == null || keyToken.isBlank()) {
            return "";
        }
        int key = source.indexOf(keyToken);
        if (key < 0) {
            return "";
        }
        int i = key + keyToken.length();
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
            i++;
        }
        if (i + 1 >= source.length() || source.charAt(i) != '<' || source.charAt(i + 1) != '<') {
            return "";
        }
        i += 2;
        int depth = 1;
        int start = i;
        while (i + 1 < source.length()) {
            if (source.charAt(i) == '<' && source.charAt(i + 1) == '<') {
                depth++;
                i += 2;
                continue;
            }
            if (source.charAt(i) == '>' && source.charAt(i + 1) == '>') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i);
                }
                i += 2;
                continue;
            }
            i++;
        }
        return "";
    }

    public static List<Integer> parseReferenceArray(String source) {
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

    private static String safeLower(String v) {
        return v == null ? "" : v.toLowerCase(Locale.ROOT);
    }

    private static String contentTypeByDict(String dictLower) {
        if (dictLower.contains("/dctdecode")) {
            return "image/jpeg";
        }
        if (dictLower.contains("/flatedecode")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    private void checkImageCount(int count) {
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
}
