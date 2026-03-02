package yueyang.vostok.security.xss;

/**
 * XSS 输入净化工具（Ext1）。
 *
 * <p>对用户输入中的 HTML 危险字符进行实体编码，使其在浏览器中以文本形式呈现，
 * 而不是被解析为 HTML 标签或脚本。适用于将用户输入直接嵌入 HTML 输出的场景。
 *
 * <p>编码规则：
 * <ul>
 *   <li>{@code &}  → {@code &amp;}  （必须最先替换，否则会二次编码其他实体）</li>
 *   <li>{@code <}  → {@code &lt;}</li>
 *   <li>{@code >}  → {@code &gt;}</li>
 *   <li>{@code "}  → {@code &quot;}</li>
 *   <li>{@code '}  → {@code &#x27;}</li>
 *   <li>{@code `}  → {@code &#x60;}（防止反引号在某些模板引擎中触发执行）</li>
 * </ul>
 */
public final class VKXssSanitizer {

    private VKXssSanitizer() {
    }

    /**
     * 对输入字符串进行 HTML 实体编码，使 XSS 载荷失效。
     * null 输入返回 null。
     *
     * @param input 原始用户输入
     * @return 安全的、经过 HTML 实体编码的字符串
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // & 必须第一个替换，避免对后续编码结果（如 &lt;）再次编码
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("`", "&#x60;");
    }
}
