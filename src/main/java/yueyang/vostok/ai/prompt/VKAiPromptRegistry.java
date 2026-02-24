package yueyang.vostok.ai.prompt;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VKAiPromptRegistry {
    private final ConcurrentHashMap<String, VKAiPromptTemplate> templates = new ConcurrentHashMap<>();

    public void register(VKAiPromptTemplate template) {
        if (template == null || template.getName() == null || template.getName().isBlank()) {
            throw new IllegalArgumentException("Prompt template/name is blank");
        }
        templates.put(template.getName().trim(), template);
    }

    public VKAiPromptTemplate get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return templates.get(name.trim());
    }

    public Map<String, String> render(String name, Map<String, ?> vars) {
        VKAiPromptTemplate template = get(name);
        if (template == null) {
            throw new IllegalArgumentException("Prompt template not found: " + name);
        }
        return template.render(vars);
    }

    public Set<String> names() {
        return Set.copyOf(templates.keySet());
    }

    public void clear() {
        templates.clear();
    }
}
