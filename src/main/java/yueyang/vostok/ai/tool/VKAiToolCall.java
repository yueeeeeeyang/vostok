package yueyang.vostok.ai.tool;

public class VKAiToolCall {
    private String id;
    private String name;
    private String argumentsJson;

    public VKAiToolCall() {
    }

    public VKAiToolCall(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson;
    }

    public String getId() {
        return id;
    }

    public VKAiToolCall id(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public VKAiToolCall name(String name) {
        this.name = name;
        return this;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public VKAiToolCall argumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
        return this;
    }
}
