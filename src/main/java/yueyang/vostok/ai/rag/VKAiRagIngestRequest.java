package yueyang.vostok.ai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public class VKAiRagIngestRequest {
    private String profileName;
    private String model;
    private String documentId;
    private String version = "v1";
    private String text;
    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private boolean deduplicate = true;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public String getProfileName() {
        return profileName;
    }

    public VKAiRagIngestRequest profile(String profileName) {
        this.profileName = profileName;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiRagIngestRequest model(String model) {
        this.model = model;
        return this;
    }

    public String getDocumentId() {
        return documentId;
    }

    public VKAiRagIngestRequest documentId(String documentId) {
        this.documentId = documentId;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public VKAiRagIngestRequest version(String version) {
        if (version != null && !version.isBlank()) {
            this.version = version.trim();
        }
        return this;
    }

    public String getText() {
        return text;
    }

    public VKAiRagIngestRequest text(String text) {
        this.text = text;
        return this;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public VKAiRagIngestRequest chunkSize(int chunkSize) {
        this.chunkSize = Math.max(1, chunkSize);
        return this;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public VKAiRagIngestRequest chunkOverlap(int chunkOverlap) {
        this.chunkOverlap = Math.max(0, chunkOverlap);
        return this;
    }

    public boolean isDeduplicate() {
        return deduplicate;
    }

    public VKAiRagIngestRequest deduplicate(boolean deduplicate) {
        this.deduplicate = deduplicate;
        return this;
    }

    public Map<String, String> getMetadata() {
        return Map.copyOf(metadata);
    }

    public VKAiRagIngestRequest metadata(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            metadata.put(key, value);
        }
        return this;
    }

    public VKAiRagIngestRequest metadata(Map<String, String> values) {
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                metadata(e.getKey(), e.getValue());
            }
        }
        return this;
    }
}
