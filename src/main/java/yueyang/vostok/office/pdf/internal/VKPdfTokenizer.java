package yueyang.vostok.office.pdf.internal;

import java.util.ArrayList;
import java.util.List;

/** PDF 内容流分词器（用于文本操作符解析）。 */
public final class VKPdfTokenizer {
    private VKPdfTokenizer() {
    }

    public static List<String> tokenize(String content) {
        List<String> out = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return out;
        }
        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(') {
                int end = scanPdfString(content, i);
                out.add(content.substring(i, end));
                i = end;
                continue;
            }
            if (c == '[') {
                int end = scanBracket(content, i, '[', ']');
                out.add(content.substring(i, end));
                i = end;
                continue;
            }
            int j = i + 1;
            while (j < content.length() && !Character.isWhitespace(content.charAt(j))) {
                char x = content.charAt(j);
                if (x == '(' || x == '[' || x == ']') {
                    break;
                }
                j++;
            }
            out.add(content.substring(i, j));
            i = j;
        }
        return out;
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

    private static int scanBracket(String s, int start, char left, char right) {
        int i = start + 1;
        int depth = 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == left) {
                depth++;
            } else if (c == right) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
            i++;
        }
        return s.length();
    }
}
