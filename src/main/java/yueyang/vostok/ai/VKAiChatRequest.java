package yueyang.vostok.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VKAiChatRequest {
    private String profileName;
    private String systemPrompt;
    private final List<VKAiMessage> messages = new ArrayList<>();
    private Double temperature;
    private Integer maxTokens;
    private String model;
    private final Set<String> allowedTools = new LinkedHashSet<>();
    private boolean stream;
    private boolean historyTrimEnabled = true;
    private Integer historyMaxMessages;
    private Integer historyMaxChars;

    public String getProfileName() {
        return profileName;
    }

    public VKAiChatRequest profile(String profileName) {
        this.profileName = profileName;
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

    public boolean isStream() {
        return stream;
    }

    public VKAiChatRequest stream(boolean stream) {
        this.stream = stream;
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

    public boolean isHistoryTrimEnabled() {
        return historyTrimEnabled;
    }

    public VKAiChatRequest historyTrimEnabled(boolean historyTrimEnabled) {
        this.historyTrimEnabled = historyTrimEnabled;
        return this;
    }

    public Integer getHistoryMaxMessages() {
        return historyMaxMessages;
    }

    public VKAiChatRequest historyMaxMessages(Integer historyMaxMessages) {
        this.historyMaxMessages = historyMaxMessages;
        return this;
    }

    public Integer getHistoryMaxChars() {
        return historyMaxChars;
    }

    public VKAiChatRequest historyMaxChars(Integer historyMaxChars) {
        this.historyMaxChars = historyMaxChars;
        return this;
    }
}
