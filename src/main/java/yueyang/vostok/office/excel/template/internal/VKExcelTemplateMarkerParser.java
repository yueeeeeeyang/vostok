package yueyang.vostok.office.excel.template.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 行级模板标记解析器。 */
public final class VKExcelTemplateMarkerParser {
    private static final Pattern LOOP_START = Pattern.compile(
            "^\\{\\{\\s*#([a-zA-Z0-9_\\.]+)\\s+as\\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\\s+keepPlaceholderRows=(true|false))?\\s*}}$");
    private static final Pattern COND_START = Pattern.compile("^\\{\\{\\s*\\?([a-zA-Z0-9_\\.]+)\\s*}}$");
    private static final Pattern END = Pattern.compile("^\\{\\{\\s*/([a-zA-Z0-9_\\.]+)\\s*}}$");

    private VKExcelTemplateMarkerParser() {
    }

    public static Marker parse(String text) {
        if (text == null) {
            return null;
        }
        String v = text.trim();
        if (v.isEmpty()) {
            return null;
        }

        Matcher loop = LOOP_START.matcher(v);
        if (loop.matches()) {
            String key = loop.group(1);
            String alias = loop.group(2);
            String keep = loop.group(3);
            Boolean keepRows = keep == null ? null : Boolean.parseBoolean(keep.toLowerCase(Locale.ROOT));
            return new Marker(MarkerType.LOOP_START, key, alias, keepRows);
        }

        Matcher cond = COND_START.matcher(v);
        if (cond.matches()) {
            return new Marker(MarkerType.COND_START, cond.group(1), null, null);
        }

        Matcher end = END.matcher(v);
        if (end.matches()) {
            return new Marker(MarkerType.END, end.group(1), null, null);
        }
        return null;
    }

    public static void assertAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "Excel template loop alias is blank");
        }
        if ("this".equals(alias)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR, "Excel template loop alias 'this' is reserved");
        }
    }

    public enum MarkerType {
        LOOP_START,
        COND_START,
        END
    }

    public record Marker(MarkerType type, String key, String alias, Boolean keepPlaceholderRows) {
    }
}
