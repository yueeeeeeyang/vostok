package yueyang.vostok.ai.rag;

import java.util.List;

public class VKAiRagIngestResult {
    private final String documentId;
    private final String version;
    private final int totalChunks;
    private final int insertedChunks;
    private final int skippedDuplicateChunks;
    private final List<String> chunkIds;

    public VKAiRagIngestResult(String documentId,
                               String version,
                               int totalChunks,
                               int insertedChunks,
                               int skippedDuplicateChunks,
                               List<String> chunkIds) {
        this.documentId = documentId;
        this.version = version;
        this.totalChunks = totalChunks;
        this.insertedChunks = insertedChunks;
        this.skippedDuplicateChunks = skippedDuplicateChunks;
        this.chunkIds = chunkIds == null ? List.of() : List.copyOf(chunkIds);
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getVersion() {
        return version;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getInsertedChunks() {
        return insertedChunks;
    }

    public int getSkippedDuplicateChunks() {
        return skippedDuplicateChunks;
    }

    public List<String> getChunkIds() {
        return chunkIds;
    }
}
