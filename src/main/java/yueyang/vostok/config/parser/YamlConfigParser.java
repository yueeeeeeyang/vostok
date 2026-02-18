package yueyang.vostok.config.parser;

import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class YamlConfigParser implements VKConfigParser {
    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    @Override
    public Map<String, String> parse(String sourceId, InputStream inputStream) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new VKConfigException(VKConfigErrorCode.IO_ERROR, "Failed to read yaml: " + sourceId, e);
        }

        Object root = parseYaml(lines, sourceId);
        Map<String, String> out = new LinkedHashMap<>();
        flatten("", root, out);
        return out;
    }

    private Object parseYaml(List<String> lines, String sourceId) {
        List<YamlLine> effective = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String noComment = stripComment(raw);
            if (noComment.trim().isEmpty()) {
                continue;
            }
            int indent = countIndent(noComment);
            if ((indent & 1) == 1) {
                throw parseError(sourceId, i + 1, "Indentation must use even spaces");
            }
            effective.add(new YamlLine(i + 1, indent, noComment.trim()));
        }

        if (effective.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }

        ParseResult parsed = parseBlock(effective, 0, effective.get(0).indent, sourceId);
        if (parsed.next != effective.size()) {
            YamlLine line = effective.get(parsed.next);
            throw parseError(sourceId, line.number, "Unexpected token: " + line.text);
        }
        return parsed.value;
    }

    private ParseResult parseBlock(List<YamlLine> lines, int index, int indent, String sourceId) {
        if (index >= lines.size()) {
            return new ParseResult(new LinkedHashMap<String, Object>(), index);
        }

        YamlLine first = lines.get(index);
        if (first.indent != indent) {
            throw parseError(sourceId, first.number, "Invalid indentation");
        }

        if (first.text.startsWith("- ")) {
            return parseList(lines, index, indent, sourceId);
        }
        return parseMap(lines, index, indent, sourceId);
    }

    private ParseResult parseMap(List<YamlLine> lines, int index, int indent, String sourceId) {
        Map<String, Object> map = new LinkedHashMap<>();
        int i = index;

        while (i < lines.size()) {
            YamlLine line = lines.get(i);
            if (line.indent < indent) {
                break;
            }
            if (line.indent > indent) {
                throw parseError(sourceId, line.number, "Unexpected deeper indentation");
            }
            if (line.text.startsWith("- ")) {
                break;
            }

            int colon = line.text.indexOf(':');
            if (colon <= 0) {
                throw parseError(sourceId, line.number, "Invalid map item: " + line.text);
            }

            String key = line.text.substring(0, colon).trim();
            String rest = line.text.substring(colon + 1).trim();
            if (key.isEmpty()) {
                throw parseError(sourceId, line.number, "Empty key");
            }

            if (!rest.isEmpty()) {
                map.put(key, unquote(rest));
                i++;
                continue;
            }

            i++;
            if (i >= lines.size() || lines.get(i).indent <= indent) {
                map.put(key, "");
                continue;
            }

            ParseResult child = parseBlock(lines, i, lines.get(i).indent, sourceId);
            map.put(key, child.value);
            i = child.next;
        }

        return new ParseResult(map, i);
    }

    private ParseResult parseList(List<YamlLine> lines, int index, int indent, String sourceId) {
        List<Object> list = new ArrayList<>();
        int i = index;

        while (i < lines.size()) {
            YamlLine line = lines.get(i);
            if (line.indent < indent) {
                break;
            }
            if (line.indent > indent) {
                throw parseError(sourceId, line.number, "Unexpected deeper indentation in list");
            }
            if (!line.text.startsWith("- ")) {
                break;
            }

            String item = line.text.substring(2).trim();
            i++;

            if (item.isEmpty()) {
                if (i < lines.size() && lines.get(i).indent > indent) {
                    ParseResult child = parseBlock(lines, i, lines.get(i).indent, sourceId);
                    list.add(child.value);
                    i = child.next;
                } else {
                    list.add("");
                }
                continue;
            }

            int colon = item.indexOf(':');
            if (colon > 0) {
                String key = item.substring(0, colon).trim();
                String rest = item.substring(colon + 1).trim();
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put(key, rest.isEmpty() ? "" : unquote(rest));
                list.add(itemMap);
                continue;
            }

            list.add(unquote(item));
        }

        return new ParseResult(list, i);
    }

    private void flatten(String prefix, Object node, Map<String, String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String next = prefix.isEmpty() ? key : prefix + "." + key;
                flatten(next, entry.getValue(), out);
            }
            return;
        }

        if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String next = prefix + "[" + i + "]";
                flatten(next, list.get(i), out);
            }
            return;
        }

        if (!prefix.isEmpty()) {
            out.put(prefix, node == null ? "" : String.valueOf(node));
        }
    }

    private String stripComment(String line) {
        boolean single = false;
        boolean dbl = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !dbl) {
                single = !single;
            } else if (ch == '"' && !single) {
                dbl = !dbl;
            }

            if (ch == '#' && !single && !dbl) {
                break;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private int countIndent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2) {
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    private VKConfigException parseError(String sourceId, int line, String message) {
        return new VKConfigException(VKConfigErrorCode.PARSE_ERROR,
                "Yaml parse error at " + sourceId + ":" + line + " - " + message);
    }

    private record ParseResult(Object value, int next) {
    }

    private record YamlLine(int number, int indent, String text) {
    }
}
