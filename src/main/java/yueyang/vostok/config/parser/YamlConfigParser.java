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

/**
 * 轻量 YAML 配置解析器（无第三方依赖）。
 * <p>
 * 支持特性：
 * <ul>
 *   <li>任意一致缩进（1 空格、2 空格、3 空格等均可，不再强制偶数）</li>
 *   <li>Mapping（key: value）及 Sequence（- item）</li>
 *   <li>列表项内联多键 Map（{@code - key1: v1} + 后续缩进的 {@code key2: v2}）</li>
 *   <li>引号值（单引号、双引号，去除外层引号）</li>
 *   <li>行注释（# 注释，引号内的 # 不视为注释）</li>
 * </ul>
 * 不支持：多行字符串块（|/>）、锚点（&）、引用（*）、流式集合（{}、[]）。
 */
public class YamlConfigParser implements VKConfigParser {

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
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

    // ── 解析入口 ──────────────────────────────────────────────────────────────

    private Object parseYaml(List<String> lines, String sourceId) {
        List<YamlLine> effective = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String noComment = stripComment(raw);
            if (noComment.isBlank()) {
                continue;
            }
            int indent = countIndent(noComment);
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

    // ── 递归块解析 ────────────────────────────────────────────────────────────

    private ParseResult parseBlock(List<YamlLine> lines, int index, int indent, String sourceId) {
        if (index >= lines.size()) {
            return new ParseResult(new LinkedHashMap<String, Object>(), index);
        }
        YamlLine first = lines.get(index);
        if (first.indent != indent) {
            throw parseError(sourceId, first.number, "Invalid indentation");
        }
        if (first.text.startsWith("- ") || first.text.equals("-")) {
            return parseList(lines, index, indent, sourceId);
        }
        return parseMap(lines, index, indent, sourceId);
    }

    // ── Mapping 解析 ──────────────────────────────────────────────────────────

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
            if (line.text.startsWith("- ") || line.text.equals("-")) {
                break; // 进入父级的 list 分支
            }

            int colon = line.text.indexOf(':');
            if (colon <= 0) {
                throw parseError(sourceId, line.number, "Invalid map item: " + line.text);
            }

            String key  = line.text.substring(0, colon).trim();
            String rest = line.text.substring(colon + 1).trim();
            if (key.isEmpty()) {
                throw parseError(sourceId, line.number, "Empty key");
            }

            i++;

            if (!rest.isEmpty()) {
                // key: scalar_value
                map.put(key, unquote(rest));
                continue;
            }

            // key: （后续为子结构或空值）
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

    // ── Sequence 解析 ─────────────────────────────────────────────────────────

    /**
     * 解析 YAML 列表（Sequence）。
     * <p>
     * Bug 6 修复：正确处理列表项内联多键 Map，如：
     * <pre>
     * items:
     *   - name: foo
     *     value: bar  # 此行与 name 同为列表项的字段，不丢失
     * </pre>
     * 当 {@code - key: val} 之后有缩进更深的行时，这些行会作为同一列表项 Map 的续行解析。
     */
    private ParseResult parseList(List<YamlLine> lines, int index, int indent, String sourceId) {
        List<Object> list = new ArrayList<>();
        int i = index;

        while (i < lines.size()) {
            YamlLine line = lines.get(i);
            if (line.indent < indent) {
                break;
            }
            if (line.indent > indent) {
                // 列表项之间不应出现更深缩进的孤立行
                throw parseError(sourceId, line.number, "Unexpected deeper indentation in list");
            }
            if (!line.text.startsWith("- ") && !line.text.equals("-")) {
                break; // 切回父级 map 分支
            }

            // 取 '- ' 之后的内容
            String item = line.text.length() > 2 ? line.text.substring(2).trim() : "";
            i++;

            if (item.isEmpty()) {
                // '- ' 后无内容 → 后续缩进行为子 block，或空字符串
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
                // '- key: val' 格式 → 内联 Map 的第一个字段
                String key  = item.substring(0, colon).trim();
                String rest = item.substring(colon + 1).trim();
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put(key, rest.isEmpty() ? "" : unquote(rest));

                // 检查后续是否有属于同一列表项的续行（缩进比列表 indent 深）
                if (i < lines.size() && lines.get(i).indent > indent) {
                    int childIndent = lines.get(i).indent;
                    ParseResult childResult = parseMap(lines, i, childIndent, sourceId);
                    // 将续行解析出的字段合并进同一 Map
                    if (childResult.value instanceof Map<?, ?> extraMap) {
                        for (Map.Entry<?, ?> e : extraMap.entrySet()) {
                            itemMap.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                    i = childResult.next;
                }
                list.add(itemMap);
                continue;
            }

            // 纯量列表项
            list.add(unquote(item));
        }

        return new ParseResult(list, i);
    }

    // ── 扁平化输出 ────────────────────────────────────────────────────────────

    private void flatten(String prefix, Object node, Map<String, String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key  = String.valueOf(entry.getKey());
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

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    /**
     * 去除行内注释（# 及其后内容），引号包围的 # 不视为注释。
     * <p>
     * Perf 5 优化：先用 indexOf('#') 快速筛，仅对含 '#' 的行进入精细扫描，
     * 多数无注释行无额外开销。
     */
    private String stripComment(String line) {
        int hashPos = line.indexOf('#');
        if (hashPos < 0) {
            return line; // 无 '#'，直接返回
        }

        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
            }
            if (ch == '#' && !inSingle && !inDouble) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private int countIndent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** 去除值两端的匹配引号（单引号或双引号）。 */
    private String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2) {
            char first = v.charAt(0);
            char last  = v.charAt(v.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    private VKConfigException parseError(String sourceId, int line, String message) {
        return new VKConfigException(VKConfigErrorCode.PARSE_ERROR,
                "Yaml parse error at " + sourceId + ":" + line + " - " + message);
    }

    // ── 内部记录 ──────────────────────────────────────────────────────────────

    private record ParseResult(Object value, int next) {
    }

    private record YamlLine(int number, int indent, String text) {
    }
}
