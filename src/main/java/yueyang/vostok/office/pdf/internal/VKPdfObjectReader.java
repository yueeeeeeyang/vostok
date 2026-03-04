package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读取 PDF 对象（obj/endobj + stream/endstream）。 */
public final class VKPdfObjectReader {
    private static final Pattern OBJECT_PATTERN = Pattern.compile("(?s)(\\d+)\\s+(\\d+)\\s+obj\\s*(.*?)\\s*endobj");

    private VKPdfObjectReader() {
    }

    public static Map<Integer, PdfObject> readObjects(byte[] pdfBytes, int maxObjects) {
        Map<Integer, PdfObject> objects = new LinkedHashMap<>();
        if (pdfBytes == null || pdfBytes.length == 0) {
            return objects;
        }
        String text = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        Matcher m = OBJECT_PATTERN.matcher(text);
        while (m.find()) {
            int id;
            try {
                id = Integer.parseInt(m.group(1));
            } catch (Exception e) {
                continue;
            }
            String body = m.group(3);
            byte[] streamBytes = null;
            String dictionary = body == null ? "" : body.trim();

            if (body != null) {
                int streamIdx = body.indexOf("stream");
                int endstreamIdx = body.lastIndexOf("endstream");
                if (streamIdx >= 0 && endstreamIdx > streamIdx) {
                    dictionary = body.substring(0, streamIdx).trim();
                    byte[] bodyBytes = body.getBytes(StandardCharsets.ISO_8859_1);
                    int start = streamIdx + "stream".length();
                    if (start < bodyBytes.length && bodyBytes[start] == '\r') {
                        start++;
                    }
                    if (start < bodyBytes.length && bodyBytes[start] == '\n') {
                        start++;
                    }
                    int end = endstreamIdx;
                    while (end > start && (bodyBytes[end - 1] == '\n' || bodyBytes[end - 1] == '\r')) {
                        end--;
                    }
                    streamBytes = start < end ? Arrays.copyOfRange(bodyBytes, start, end) : new byte[0];
                }
            }

            objects.put(id, new PdfObject(id, dictionary, body == null ? "" : body, streamBytes));
            if (maxObjects > 0 && objects.size() > maxObjects) {
                throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                        "PDF objects exceed limit: " + objects.size() + " > " + maxObjects);
            }
        }

        if (objects.isEmpty()) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "PDF object list is empty");
        }

        return objects;
    }

    /** PDF 对象视图。 */
    public record PdfObject(int id, String dictionary, String body, byte[] streamBytes) {
    }
}
