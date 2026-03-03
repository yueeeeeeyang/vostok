package yueyang.vostok.security.field;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 字段加密类型枚举。
 *
 * <p>每个枚举值对应一种 Java 类型，提供双向序列化方法：
 * <ul>
 *   <li>{@link #toBytes(Object)}：将 Java 对象序列化为字节数组，供 AES-GCM 加密。</li>
 *   <li>{@link #fromBytes(byte[])}：将解密后的字节数组反序列化为对应 Java 对象。</li>
 * </ul>
 *
 * <p>类型转换规则：{@code toBytes} 入参类型不符时由 JVM 抛出 {@link ClassCastException}，
 * 无需显式类型检查。
 *
 * <table>
 *   <tr><th>枚举值</th><th>入参类型</th><th>fromBytes 返回类型</th><th>序列化格式</th></tr>
 *   <tr><td>STRING</td><td>String</td><td>String</td><td>UTF-8</td></tr>
 *   <tr><td>INTEGER</td><td>Number</td><td>Integer</td><td>十进制字符串 UTF-8</td></tr>
 *   <tr><td>LONG</td><td>Number</td><td>Long</td><td>十进制字符串 UTF-8</td></tr>
 *   <tr><td>DOUBLE</td><td>Number</td><td>Double</td><td>toString() UTF-8</td></tr>
 *   <tr><td>BIG_DECIMAL</td><td>BigDecimal</td><td>BigDecimal</td><td>toString() UTF-8</td></tr>
 *   <tr><td>LOCAL_DATE</td><td>LocalDate</td><td>LocalDate</td><td>yyyy-MM-dd UTF-8</td></tr>
 *   <tr><td>LOCAL_DATE_TIME</td><td>LocalDateTime</td><td>LocalDateTime</td><td>ISO 格式 UTF-8</td></tr>
 *   <tr><td>BOOLEAN</td><td>Boolean</td><td>Boolean</td><td>"1"/"0" 单字节</td></tr>
 *   <tr><td>BYTES</td><td>byte[]</td><td>byte[]</td><td>直接存储，无中转</td></tr>
 * </table>
 */
public enum VKFieldType {

    STRING {
        @Override
        public byte[] toBytes(Object value) {
            return ((String) value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    },

    INTEGER {
        @Override
        public byte[] toBytes(Object value) {
            return String.valueOf(((Number) value).intValue()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return Integer.parseInt(new String(bytes, StandardCharsets.UTF_8));
        }
    },

    LONG {
        @Override
        public byte[] toBytes(Object value) {
            return String.valueOf(((Number) value).longValue()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
    },

    DOUBLE {
        @Override
        public byte[] toBytes(Object value) {
            return String.valueOf(((Number) value).doubleValue()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return Double.parseDouble(new String(bytes, StandardCharsets.UTF_8));
        }
    },

    BIG_DECIMAL {
        @Override
        public byte[] toBytes(Object value) {
            return ((BigDecimal) value).toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return new BigDecimal(new String(bytes, StandardCharsets.UTF_8));
        }
    },

    LOCAL_DATE {
        @Override
        public byte[] toBytes(Object value) {
            return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return LocalDate.parse(new String(bytes, StandardCharsets.UTF_8), DateTimeFormatter.ISO_LOCAL_DATE);
        }
    },

    LOCAL_DATE_TIME {
        @Override
        public byte[] toBytes(Object value) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return LocalDateTime.parse(new String(bytes, StandardCharsets.UTF_8), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    },

    BOOLEAN {
        @Override
        public byte[] toBytes(Object value) {
            // "1" 表示 true，"0" 表示 false
            return ((Boolean) value) ? new byte[]{'1'} : new byte[]{'0'};
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return bytes.length > 0 && bytes[0] == '1';
        }
    },

    BYTES {
        @Override
        public byte[] toBytes(Object value) {
            // 二进制类型直接存储，无任何中转或编码
            return (byte[]) value;
        }

        @Override
        public Object fromBytes(byte[] bytes) {
            return bytes;
        }
    };

    /**
     * 将 Java 对象序列化为字节数组，供 AES-GCM 加密。
     *
     * @param value 待序列化的值；类型不匹配时抛 {@link ClassCastException}
     * @return 序列化后的字节数组
     */
    public abstract byte[] toBytes(Object value);

    /**
     * 将解密后的字节数组反序列化为对应 Java 对象。
     *
     * @param bytes 解密后的原始字节数组
     * @return 反序列化后的 Java 对象（具体类型见枚举值表格）
     */
    public abstract Object fromBytes(byte[] bytes);
}
