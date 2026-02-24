package yueyang.vostok.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VKAiChatRequest {
    private String clientName;
    private String systemPrompt;
    private final List<VKAiMessage> messages = new ArrayList<>();
    private Double temperature;
    private Integer maxTokens;
    private String model;
    private final Set<String> allowedTools = new LinkedHashSet<>();

    public String getClientName() {
        return clientName;
    }

    public VKAiChatRequest client(String clientName) {
        this.clientName = clientName;
        return this;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public VKAiChatRequest system(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public List<VKAiMessage> getMessages() {
        return List.copyOf(messages);
    }

    public VKAiChatRequest message(String role, String content) {
        this.messages.add(new VKAiMessage(role, content));
        return this;
    }

    public VKAiChatRequest message(VKAiMessage message) {
        if (message != null) {
            this.messages.add(message);
        }
        return this;
    }

    public Double getTemperature() {
        return temperature;
    }

    public VKAiChatRequest temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public VKAiChatRequest maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiChatRequest model(String model) {
        this.model = model;
        return this;
    }

    public Set<String> getAllowedTools() {
        return Set.copyOf(allowedTools);
    }

    public VKAiChatRequest allowTool(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            this.allowedTools.add(toolName.trim());
        }
        return this;
    }

    public VKAiChatRequest allowTools(String... toolNames) {
        if (toolNames == null) {
            return this;
        }
        for (String toolName : toolNames) {
            allowTool(toolName);
        }
        return this;
    }
}
