package yueyang.vostok.security.nosql;

import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VKSecurityRiskLevel;

import java.util.regex.Pattern;

/**
 * NoSQL 注入检测扫描器（Ext8）。
 *
 * <p>主要面向 MongoDB 的 Operator 注入和 JavaScript 注入场景。
 * 攻击者通过在 JSON 请求参数中注入 MongoDB 操作符，绕过认证或执行任意查询。
 *
 * <p>检测维度：
 * <ol>
 *   <li>{@code $where} JavaScript 注入：最高风险，可直接执行任意 JS</li>
 *   <li>JSON 对象中的操作符注入：{@code {"$ne": null}} 等绕过认证的经典模式</li>
 *   <li>操作符单独出现：{@code $gt}, {@code $in} 等，可能是参数污染</li>
 * </ol>
 */
public final class VKNoSqlInjectionScanner {

    /**
     * {@code $where} 后跟引号或冒号，说明存在 JS 代码注入意图，最高风险。
     * 例：{@code {"$where": "this.password == 'x'"}}
     */
    private static final Pattern WHERE_CLAUSE = Pattern.compile(
            "\\$where\\s*[\"':]",
            Pattern.CASE_INSENSITIVE);

    /**
     * JSON 对象中操作符注入：{@code {"$ne": ...}} / {@code {"$gt": ...}} 等。
     * 攻击者将标量参数替换为对象从而绕过等值比较。
     */
    private static final Pattern OPERATOR_IN_OBJECT = Pattern.compile(
            "\\{\\s*[\"']\\s*\\$[a-z]{2,15}\\s*[\"']\\s*:",
            Pattern.CASE_INSENSITIVE);

    /**
     * MongoDB 常见查询/更新操作符集合。
     * 单独出现时为 MEDIUM（可能是正常 JSON），配合上下文分析。
     */
    private static final Pattern NOSQL_OPERATOR = Pattern.compile(
            "\\$(?:gt|gte|lt|lte|ne|nin|in|or|and|not|nor|exists|type|all|size|"
            + "regex|text|where|elemMatch|slice|push|pull|set|unset|inc|rename|"
            + "addToSet|pop|bit|currentDate)\\b",
            Pattern.CASE_INSENSITIVE);

    private VKNoSqlInjectionScanner() {
    }

    /**
     * 检测输入是否存在 NoSQL 注入风险。
     *
     * @param input 待检测的字符串（通常是 JSON 请求体或参数值）
     * @return 检测结果
     */
    public static VKSecurityCheckResult check(String input) {
        if (input == null || input.isBlank()) {
            return VKSecurityCheckResult.safe();
        }

        // $where JS 注入：最高风险，可执行任意 JavaScript
        if (WHERE_CLAUSE.matcher(input).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 9,
                    "Detected $where JavaScript injection in NoSQL query", "nosql-where-injection");
        }

        // JSON 对象中操作符键值注入（如 {"$ne": null} 绕过密码验证）
        if (OPERATOR_IN_OBJECT.matcher(input).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.HIGH, 8,
                    "Detected operator injection in JSON object (NoSQL bypass)", "nosql-operator-object");
        }

        // 操作符单独出现，可能是参数污染或注入尝试
        if (NOSQL_OPERATOR.matcher(input).find()) {
            return VKSecurityCheckResult.unsafe(VKSecurityRiskLevel.MEDIUM, 6,
                    "Detected NoSQL operator in input", "nosql-operator");
        }

        return VKSecurityCheckResult.safe();
    }
}
