package yueyang.vostok.office.pdf.internal;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 提取 PDF 内容流中的文本（Tj / TJ）。 */
public final class VKPdfTextScanner {
    private static final Pattern TJ_ARRAY_PATTERN = Pattern.compile("\\[(.*?)]\\s*TJ", Pattern.DOTALL);

    private VKPdfTextScanner() {
    }

    public static String extractText(byte[] contentBytes) {
        if (contentBytes == null || contentBytes.length == 0) {
            return "";
        }
        String content = new String(contentBytes, StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder();

        // 解析 (...) Tj
        int i = 0;
        while (i < content.length()) {
            if (content.charAt(i) != '(') {
                i++;
                continue;
            }
            int end = scanPdfString(content, i);
            String strToken = content.substring(i, end);
            int j = skipSpaces(content, end);
            if (startsWithOperator(content, j, "Tj") || startsWithOperator(content, j, "'")) {
                out.append(decodePdfStringLiteral(strToken));
                out.append('\n');
            }
            i = Math.max(end, i + 1);
        }

        // 解析 [...] TJ
        Matcher m = TJ_ARRAY_PATTERN.matcher(content);
        while (m.find()) {
            String arr = m.group(1);
            int p = 0;
            while (p < arr.length()) {
                if (arr.charAt(p) != '(') {
                    p++;
                    continue;
                }
                int end = scanPdfString(arr, p);
                out.append(decodePdfStringLiteral(arr.substring(p, end)));
                p = Math.max(end, p + 1);
            }
            out.append('\n');
        }

        return out.toString();
    }

    public static int countNonWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int c = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isWhitespace(cp)) {
                c++;
            }
        }
        return c;
    }

    private static int scanPdfString(String s, int start) {
        int i = start + 1;
        int depth = 1;
        boolean esc = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (esc) {
                esc = false;
                i++;
                continue;
            }
            if (c == '\\') {
                esc = true;
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
            i++;
        }
        return s.length();
    }

    private static int skipSpaces(String s, int start) {
        int i = start;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean startsWithOperator(String s, int i, String op) {
        if (i < 0 || i + op.length() > s.length()) {
            return false;
        }
        if (!s.startsWith(op, i)) {
            return false;
        }
        int end = i + op.length();
        return end >= s.length() || Character.isWhitespace(s.charAt(end));
    }

    private static String decodePdfStringLiteral(String token) {
        if (token == null || token.length() < 2) {
            return "";
        }
        int start = token.charAt(0) == '(' ? 1 : 0;
        int end = token.length();
        if (end > start && token.charAt(end - 1) == ')') {
            end--;
        }
        String raw = token.substring(start, end);
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= raw.length()) {
                break;
            }
            char n = raw.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case '(', ')', '\\' -> out.append(n);
                case '\n', '\r' -> {
                    // 行连接，忽略
                    if (n == '\r' && i + 1 < raw.length() && raw.charAt(i + 1) == '\n') {
                        i++;
                    }
                }
                default -> {
                    if (n >= '0' && n <= '7') {
                        int oct = n - '0';
                        int consumed = 0;
                        while (consumed < 2 && i + 1 < raw.length()) {
                            char d = raw.charAt(i + 1);
                            if (d < '0' || d > '7') {
                                break;
                            }
                            i++;
                            consumed++;
                            oct = oct * 8 + (d - '0');
                        }
                        out.append((char) (oct & 0xFF));
                    } else {
                        out.append(n);
                    }
                }
            }
        }
        return out.toString();
    }
}
