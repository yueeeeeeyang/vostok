package yueyang.vostok.ai.rag;

import java.util.ArrayList;
import java.util.List;

public class VKAiEmbeddingRequest {
    private String profileName;
    private String model;
    private final List<String> inputs = new ArrayList<>();

    public String getProfileName() {
        return profileName;
    }

    public VKAiEmbeddingRequest profile(String profileName) {
        this.profileName = profileName;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiEmbeddingRequest model(String model) {
        this.model = model;
        return this;
    }

    public List<String> getInputs() {
        return List.copyOf(inputs);
    }

    public VKAiEmbeddingRequest input(String input) {
        if (input != null) {
            this.inputs.add(input);
        }
        return this;
    }

    public VKAiEmbeddingRequest inputs(List<String> values) {
        if (values != null) {
            for (String it : values) {
                input(it);
            }
        }
        return this;
    }
}
