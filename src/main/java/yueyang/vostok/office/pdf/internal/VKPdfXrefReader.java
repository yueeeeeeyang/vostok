package yueyang.vostok.office.pdf.internal;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** 解析传统 xref 表（startxref -> xref）。 */
public final class VKPdfXrefReader {
    private VKPdfXrefReader() {
    }

    public static Map<Integer, Long> readOffsets(byte[] pdfBytes) {
        Map<Integer, Long> out = new LinkedHashMap<>();
        if (pdfBytes == null || pdfBytes.length == 0) {
            return out;
        }
        String text = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        int idx = text.lastIndexOf("startxref");
        if (idx < 0) {
            return out;
        }
        int numStart = idx + "startxref".length();
        while (numStart < text.length() && Character.isWhitespace(text.charAt(numStart))) {
            numStart++;
        }
        int numEnd = numStart;
        while (numEnd < text.length() && Character.isDigit(text.charAt(numEnd))) {
            numEnd++;
        }
        if (numEnd <= numStart) {
            return out;
        }
        int xrefOffset;
        try {
            xrefOffset = Integer.parseInt(text.substring(numStart, numEnd));
        } catch (Exception ignore) {
            return out;
        }
        if (xrefOffset < 0 || xrefOffset >= text.length()) {
            return out;
        }
        if (!text.startsWith("xref", xrefOffset)) {
            return out;
        }

        int i = xrefOffset + 4;
        while (i < text.length()) {
            while (i < text.length() && (text.charAt(i) == '\r' || text.charAt(i) == '\n' || Character.isWhitespace(text.charAt(i)))) {
                i++;
            }
            if (i >= text.length() || text.startsWith("trailer", i)) {
                break;
            }
            int lineEnd = findLineEnd(text, i);
            String header = text.substring(i, lineEnd).trim();
            String[] hp = header.split("\\s+");
            if (hp.length != 2) {
                break;
            }
            int startObj;
            int count;
            try {
                startObj = Integer.parseInt(hp[0]);
                count = Integer.parseInt(hp[1]);
            } catch (Exception e) {
                break;
            }
            i = lineEnd;
            for (int k = 0; k < count && i < text.length(); k++) {
                while (i < text.length() && (text.charAt(i) == '\r' || text.charAt(i) == '\n')) {
                    i++;
                }
                int rowEnd = findLineEnd(text, i);
                if (rowEnd <= i) {
                    break;
                }
                String row = text.substring(i, rowEnd).trim();
                String[] rp = row.split("\\s+");
                if (rp.length >= 3) {
                    try {
                        long off = Long.parseLong(rp[0]);
                        if ("n".equals(rp[2])) {
                            out.put(startObj + k, off);
                        }
                    } catch (Exception ignore) {
                    }
                }
                i = rowEnd;
            }
        }
        return out;
    }

    private static int findLineEnd(String text, int start) {
        int i = start;
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        return i;
    }
}
