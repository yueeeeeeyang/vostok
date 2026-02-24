package yueyang.vostok.ai.rag;

import java.util.ArrayList;
import java.util.List;

public class VKAiRerankRequest {
    private String clientName;
    private String model;
    private String query;
    private final List<String> documents = new ArrayList<>();
    private Integer topK;

    public String getClientName() {
        return clientName;
    }

    public VKAiRerankRequest client(String clientName) {
        this.clientName = clientName;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiRerankRequest model(String model) {
        this.model = model;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public VKAiRerankRequest query(String query) {
        this.query = query;
        return this;
    }

    public List<String> getDocuments() {
        return List.copyOf(documents);
    }

    public VKAiRerankRequest document(String doc) {
        if (doc != null) {
            this.documents.add(doc);
        }
        return this;
    }

    public VKAiRerankRequest documents(List<String> docs) {
        if (docs != null) {
            for (String it : docs) {
                document(it);
            }
        }
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public VKAiRerankRequest topK(Integer topK) {
        this.topK = topK;
        return this;
    }
}
