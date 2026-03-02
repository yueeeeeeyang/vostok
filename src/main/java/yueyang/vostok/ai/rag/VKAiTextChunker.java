package yueyang.vostok.ai.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块工具（Perf 4：语义边界感知）。
 * 分块优先级：段落边界（\n\n）> 换行（\n）> 句子结束符 > 词语边界（空格）> 字符截断（兜底）。
 * 在 [size - overlap, size] 窗口内向左扫描，优先在语义边界处断开，保证切块语义完整。
 */
public final class VKAiTextChunker {
    private VKAiTextChunker() {
    }

    public static List<String> chunk(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int size = Math.max(1, chunkSize);
        int overlap = Math.max(0, Math.min(chunkOverlap, size - 1));
        int step = Math.max(1, size - overlap);

        List<String> out = new ArrayList<>();
        int len = text.length();
        int start = 0;
        while (start < len) {
            int rawEnd = Math.min(len, start + size);
            // 在 [start + step, rawEnd] 窗口内寻找最佳语义断点
            int end = rawEnd < len ? findBestBreak(text, start + step, rawEnd) : rawEnd;
            String part = text.substring(start, end).trim();
            if (!part.isBlank()) {
                out.add(part);
            }
            if (end >= len) {
                break;
            }
            // 下一块起始：end - overlap，保证重叠
            start = Math.max(start + 1, end - overlap);
        }
        return out;
    }

    /**
     * 在 [scanFrom, scanTo] 范围内从 scanTo 向左寻找最佳断点，优先级：
     * 段落分隔（\n\n）> 换行（\n）> 中英文句末标点 > 空格/词边界 > 无合适断点（返回 scanTo）。
     */
    private static int findBestBreak(String text, int scanFrom, int scanTo) {
        if (scanFrom >= scanTo) {
            return scanTo;
        }
        // 优先级 1：\n\n（段落）
        for (int i = scanTo - 1; i >= scanFrom; i--) {
            if (i + 1 < text.length() && text.charAt(i) == '\n' && text.charAt(i + 1) == '\n') {
                return i + 2;
            }
        }
        // 优先级 2：单独 \n
        for (int i = scanTo - 1; i >= scanFrom; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        // 优先级 3：句末标点（中英文）
        for (int i = scanTo - 1; i >= scanFrom; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == ';' || c == '；') {
                return i + 1;
            }
        }
        // 优先级 4：词边界（空格或 CJK 字符后）
        for (int i = scanTo - 1; i >= scanFrom; i--) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || Character.isIdeographic(c)) {
                return i + 1;
            }
        }
        // 兜底：无合适断点，直接在 scanTo 处截断
        return scanTo;
    }
}
