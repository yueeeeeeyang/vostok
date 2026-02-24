package yueyang.vostok.ai.rag;

import java.util.ArrayList;
import java.util.List;

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
        for (int start = 0; start < len; start += step) {
            int end = Math.min(len, start + size);
            String part = text.substring(start, end).trim();
            if (!part.isBlank()) {
                out.add(part);
            }
            if (end >= len) {
                break;
            }
        }
        return out;
    }
}
