package yueyang.vostok.web.http;

import java.util.Locale;

public final class VKCookie {
    public enum SameSite {
        LAX,
        STRICT,
        NONE
    }

    private final String name;
    private final String value;
    private String domain;
    private String path = "/";
    private long maxAge = -1;
    private boolean secure;
    private boolean httpOnly;
    private SameSite sameSite;

    public VKCookie(String name, String value) {
        this.name = name;
        this.value = value == null ? "" : value;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public String domain() {
        return domain;
    }

    public VKCookie domain(String domain) {
        this.domain = domain;
        return this;
    }

    public String path() {
        return path;
    }

    public VKCookie path(String path) {
        this.path = path == null || path.isEmpty() ? "/" : path;
        return this;
    }

    public long maxAge() {
        return maxAge;
    }

    public VKCookie maxAge(long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public boolean secure() {
        return secure;
    }

    public VKCookie secure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public boolean httpOnly() {
        return httpOnly;
    }

    public VKCookie httpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public SameSite sameSite() {
        return sameSite;
    }

    public VKCookie sameSite(SameSite sameSite) {
        this.sameSite = sameSite;
        return this;
    }

    String encodeSetCookie() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Cookie name must not be empty");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(sanitizeToken(name)).append('=').append(sanitizeValue(value));
        if (domain != null && !domain.isEmpty()) {
            sb.append("; Domain=").append(domain);
        }
        if (path != null && !path.isEmpty()) {
            sb.append("; Path=").append(path);
        }
        if (maxAge >= 0) {
            sb.append("; Max-Age=").append(maxAge);
        }
        if (secure) {
            sb.append("; Secure");
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (sameSite != null) {
            sb.append("; SameSite=").append(sameSite.name().substring(0, 1))
                    .append(sameSite.name().substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private String sanitizeToken(String s) {
        return s.replace(";", "").replace("=", "").trim();
    }

    private String sanitizeValue(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", "").replace("\n", "");
    }
}
