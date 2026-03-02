package yueyang.vostok.security.xml;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.regex.Pattern;

/**
 * XML 外部实体注入（XXE）检测扫描器（Ext3）。
 *
 * <p>XXE 攻击通过在 XML 中声明外部实体，诱导服务端读取本地文件或发起内网请求。
 * 检测维度（按风险从高到低）：
 * <ol>
 *   <li>外部资源引用：{@code <!ENTITY ... file://} / {@code http://} 等</li>
 *   <li>SYSTEM/PUBLIC 关键字：可引用外部 DTD</li>
 *   <li>实体声明：{@code <!ENTITY ...>}</li>
 *   <li>含内部子集的 DOCTYPE：{@code <!DOCTYPE ... [ ...} }</li>
 * </ol>
 */
public final class VKXxeScanner {

    /**
     * 外部资源引用：ENTITY 声明中含 URL scheme（file/http/https/ftp/gopher 等），
     * 这是 XXE 漏洞的直接利用形式，风险最高。
     */
    private static final Pattern EXT_RESOURCE = Pattern.compile(
            "<!ENTITY[^>]{0,200}(?:file|http|https|ftp|gopher|ldap|expect)://",
            Pattern.CASE_INSENSITIVE);

    /**
     * SYSTEM/PUBLIC 关键字出现在 DOCTYPE 或 ENTITY 中，
     * 说明存在外部 DTD/实体引用意图，高风险。
     */
    private static final Pattern SYSTEM_OR_PUBLIC = Pattern.compile(
            "<![A-Z]{2,10}[^>]{0,200}\\b(?:SYSTEM|PUBLIC)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 任意实体声明 {@code <!ENTITY}，包括参数实体和通用实体，中等风险。
     */
    private static final Pattern ENTITY_DECL = Pattern.compile(
            "<!ENTITY\\s+",
            Pattern.CASE_INSENSITIVE);

    /**
     * 含内部子集的 DOCTYPE（{@code <!DOCTYPE ... [}），
     * 内部子集可以定义实体，是 XXE 的前提，中等风险。
     */
    private static final Pattern DOCTYPE_WITH_SUBSET = Pattern.compile(
            "<!DOCTYPE[^\\[>]{0,200}\\[",
            Pattern.CASE_INSENSITIVE);

    private VKXxeScanner() {
    }

    /**
     * 检测 XML 输入中是否存在 XXE 注入风险。
     *
     * @param xmlInput 待检测的 XML 字符串
     * @return 检测结果；safe=false 时附带风险等级和原因说明
     */
    public static VKSecurityCheckResult check(String xmlInput) {
        if (xmlInput == null || xmlInput.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        // 级别从高到低依次检测，命中即返回（优先报最高风险）
        if (EXT_RESOURCE.matcher(xmlInput).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected external entity resource reference in XML (XXE attack)", "xxe-external-resource");
        }
        if (SYSTEM_OR_PUBLIC.matcher(xmlInput).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected SYSTEM/PUBLIC keyword in XML DOCTYPE/ENTITY (potential XXE)", "xxe-system-public");
        }
        if (ENTITY_DECL.matcher(xmlInput).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 7,
                    "Detected entity declaration in XML", "xxe-entity-decl");
        }
        if (DOCTYPE_WITH_SUBSET.matcher(xmlInput).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected DOCTYPE with internal subset in XML", "xxe-doctype-subset");
        }
        return VKSecurityCheckResult.safe();
    }
}
