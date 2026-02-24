package yueyang.vostok.ai.tool;

public interface VKAiTool {
    String name();

    String description();

    String inputJsonSchema();

    String outputJsonSchema();

    VKAiToolResult invoke(String inputJson);
}
