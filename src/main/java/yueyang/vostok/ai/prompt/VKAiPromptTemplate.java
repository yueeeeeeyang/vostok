package yueyang.vostok.ai.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

public class VKAiPromptTemplate {
    private final String name;
    private final String systemTemplate;
    private final String userTemplate;

    public VKAiPromptTemplate(String name, String systemTemplate, String userTemplate) {
        this.name = name;
        this.systemTemplate = systemTemplate;
        this.userTemplate = userTemplate;
    }

    public String getName() {
        return name;
    }

    public String getSystemTemplate() {
        return systemTemplate;
    }

    public String getUserTemplate() {
        return userTemplate;
    }

    public Map<String, String> render(Map<String, ?> vars) {
        Map<String, ?> values = vars == null ? Map.of() : vars;
        Map<String, String> out = new LinkedHashMap<>();
        out.put("system", apply(systemTemplate, values));
        out.put("user", apply(userTemplate, values));
        return out;
    }

    private String apply(String template, Map<String, ?> vars) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, ?> e : vars.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey();
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue());
            out = out.replace("${" + key + "}", value);
        }
        return out;
    }
}
