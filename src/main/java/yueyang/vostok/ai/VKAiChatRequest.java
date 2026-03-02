package yueyang.vostok.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VKAiChatRequest {
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

    /**
     * Ext 4：结构化输出格式。
     * 可选值："json_object"（通用 JSON 模式）或 "json_schema"（需配合 responseJsonSchema）。
     * 为 null 时不向 provider 传递 response_format。
     */
    private String responseFormat;

    /**
     * Ext 4：当 responseFormat 为 "json_schema" 时使用的 JSON Schema 字符串（完整 JSON）。
     */
    private String responseJsonSchema;

    /**
     * Ext 7：本次请求的 token 预算（completion + prompt 合计上限）。
     * 为 null 时使用全局配置 tokenBudgetPerRequest；为 0 时禁用预算检查。
     */
    private Integer tokenBudgetTokens;

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

    // Ext 4：结构化输出

    public String getResponseFormat() {
        return responseFormat;
    }

    /**
     * 设置 response_format.type。
     * "json_object" = 通用 JSON 输出（无 schema 校验）。
     * "json_schema" = 按 responseJsonSchema 约束输出（需调用 responseJsonSchema() 配合）。
     */
    public VKAiChatRequest responseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
        return this;
    }

    public String getResponseJsonSchema() {
        return responseJsonSchema;
    }

    /**
     * 设置 JSON Schema（JSON 字符串），与 responseFormat("json_schema") 配合使用（Ext 4）。
     * 示例：{"name":"my_schema","strict":true,"schema":{...}}
     */
    public VKAiChatRequest responseJsonSchema(String responseJsonSchema) {
        this.responseJsonSchema = responseJsonSchema;
        return this;
    }

    // Ext 7：token 预算

    public Integer getTokenBudgetTokens() {
        return tokenBudgetTokens;
    }

    /**
     * 设置本请求的 token 预算上限（Ext 7）。
     * 若实际消耗 totalTokens 超过此值，将抛出 TOKEN_BUDGET_EXCEEDED 异常。
     * 设为 0 表示不限制（覆盖全局配置）。
     */
    public VKAiChatRequest tokenBudgetTokens(Integer tokenBudgetTokens) {
        this.tokenBudgetTokens = tokenBudgetTokens;
        return this;
    }
}
