package yueyang.vostok.office.template.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.template.VKOfficeTemplateOptions;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 轻量模板引擎，支持 {{var}} / {{#list as item}}...{{/list}} / {{?cond}}...{{/cond}}。 */
public final class VKOfficeTemplateEngine {
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "\\{\\{#([a-zA-Z0-9_\\.]+)(?:\\s+as\\s+([a-zA-Z_][a-zA-Z0-9_]*))?}}(.*?)\\{\\{/\\1}}",
            Pattern.DOTALL);
    private static final Pattern COND_PATTERN = Pattern.compile("\\{\\{\\?([a-zA-Z0-9_\\.]+)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_\\.]+)\\s*}}");

    private VKOfficeTemplateEngine() {
    }

    public static String render(String template, Map<String, Object> data, VKOfficeTemplateOptions options) {
        String src = template == null ? "" : template;
        Map<String, Object> root = data == null ? Map.of() : data;
        VKOfficeTemplateOptions opt = options == null ? VKOfficeTemplateOptions.defaults() : options;
        String out = renderInternal(src, root, opt, 0);
        if (opt.maxOutputChars() > 0 && out.length() > opt.maxOutputChars()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Template output chars exceed limit: " + out.length() + " > " + opt.maxOutputChars());
        }
        return out;
    }

    private static String renderInternal(String src,
                                         Map<String, Object> data,
                                         VKOfficeTemplateOptions options,
                                         int depth) {
        if (depth > 32) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "Template nested depth too deep");
        }
        String out = src == null ? "" : src;
        out = renderListSections(out, data, options, depth);
        out = renderCondSections(out, data, options, depth);
        out = renderVariables(out, data, options);
        return out;
    }

    private static String renderListSections(String src,
                                             Map<String, Object> data,
                                             VKOfficeTemplateOptions options,
                                             int depth) {
        String input = src;
        while (true) {
            Matcher m = LIST_PATTERN.matcher(input);
            if (!m.find()) {
                return input;
            }
            StringBuilder sb = new StringBuilder(input.length() + 64);
            int last = 0;
            do {
                sb.append(input, last, m.start());
                String listKey = m.group(1);
                String alias = m.group(2);
                String body = m.group(3);
                String itemAlias = (alias == null || alias.isBlank()) ? "item" : alias.trim();
                if ("this".equals(itemAlias)) {
                    throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                            "Template list alias 'this' is reserved");
                }
                Object value = resolveValue(data, listKey);
                sb.append(renderList(body, value, data, options, depth, itemAlias));
                last = m.end();
            } while (m.find());
            sb.append(input, last, input.length());
            input = sb.toString();
        }
    }

    private static String renderCondSections(String src,
                                             Map<String, Object> data,
                                             VKOfficeTemplateOptions options,
                                             int depth) {
        String input = src;
        while (true) {
            Matcher m = COND_PATTERN.matcher(input);
            if (!m.find()) {
                return input;
            }
            StringBuilder sb = new StringBuilder(input.length() + 64);
            int last = 0;
            do {
                sb.append(input, last, m.start());
                String key = m.group(1);
                String body = m.group(2);
                Object value = resolveValue(data, key);
                if (isTruthy(value)) {
                    sb.append(renderInternal(body, data, options, depth + 1));
                }
                last = m.end();
            } while (m.find());
            sb.append(input, last, input.length());
            input = sb.toString();
        }
    }

    private static String renderList(String body,
                                     Object value,
                                     Map<String, Object> parent,
                                     VKOfficeTemplateOptions options,
                                     int depth,
                                     String alias) {
        if (value == null) {
            return "";
        }
        List<Object> items = toList(value);
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Object item : items) {
            Map<String, Object> child = new LinkedHashMap<>(parent);
            child.put(alias, item);
            out.append(renderInternal(body, child, options, depth + 1));
        }
        return out.toString();
    }

    private static String renderVariables(String src, Map<String, Object> data, VKOfficeTemplateOptions options) {
        Matcher m = VAR_PATTERN.matcher(src);
        StringBuffer sb = new StringBuffer(src.length() + 64);
        while (m.find()) {
            String key = m.group(1);
            Object value = resolveValue(data, key);
            if (value == null) {
                if (options.strictVariable()) {
                    throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "Template variable not found: " + key);
                }
                m.appendReplacement(sb, "");
                continue;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Object resolveValue(Map<String, Object> data, String key) {
        if (key == null || key.isBlank() || data == null || data.isEmpty()) {
            return null;
        }
        if (!key.contains(".")) {
            return data.get(key);
        }
        String[] parts = key.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private static boolean isTruthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0.0d;
        }
        if (v instanceof CharSequence cs) {
            return cs.length() > 0;
        }
        if (v instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (v.getClass().isArray()) {
            return Array.getLength(v) > 0;
        }
        return true;
    }

    private static List<Object> toList(Object v) {
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (v instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            for (Object o : iterable) {
                out.add(o);
            }
            return out;
        }
        if (v.getClass().isArray()) {
            int len = Array.getLength(v);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(Array.get(v, i));
            }
            return out;
        }
        return List.of(v);
    }
}
