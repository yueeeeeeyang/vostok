package yueyang.vostok.web.http;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Locale;

public final class VKRequest {
    private final String method;
    private final String path;
    private final String query;
    private final String version;
    private final Map<String, String> headers;
    private final byte[] body;
    private final boolean keepAlive;
    private final InetSocketAddress remoteAddress;
    private Map<String, String> params;
    private Map<String, String> queryParams;
    private Map<String, String> cookies;
    private Map<String, String> formFields;
    private Map<String, List<VKUploadedFile>> multipartFiles;
    private List<VKUploadedFile> allFiles;
    private String traceId;

    public VKRequest(String method, String path, String query, String version,
                     Map<String, String> headers, byte[] body,
                     boolean keepAlive, InetSocketAddress remoteAddress) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.version = version;
        this.headers = headers == null ? new HashMap<>() : headers;
        this.body = body == null ? new byte[0] : body;
        this.keepAlive = keepAlive;
        this.remoteAddress = remoteAddress;
        this.params = new HashMap<>();
        this.queryParams = null;
        this.cookies = null;
        this.formFields = null;
        this.multipartFiles = null;
        this.allFiles = null;
        this.traceId = null;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String query() {
        return query;
    }

    public String version() {
        return version;
    }

    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }

    public String header(String name) {
        if (name == null) {
            return null;
        }
        return headers.get(name.toLowerCase());
    }

    public byte[] body() {
        return body;
    }

    public String bodyText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean keepAlive() {
        return keepAlive;
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    public String traceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String param(String name) {
        if (name == null || params == null) {
            return null;
        }
        return params.get(name);
    }

    public String queryParam(String name) {
        if (name == null) {
            return null;
        }
        Map<String, String> map = ensureQueryParams();
        return map.get(name);
    }

    public Map<String, String> queryParams() {
        return ensureQueryParams();
    }

    public String cookie(String name) {
        if (name == null) {
            return null;
        }
        return ensureCookies().get(name);
    }

    public Map<String, String> cookies() {
        return ensureCookies();
    }

    public boolean isMultipart() {
        String contentType = header("content-type");
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
    }

    public String formField(String name) {
        if (name == null || formFields == null) {
            return null;
        }
        return formFields.get(name);
    }

    public Map<String, String> formFields() {
        if (formFields == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(formFields);
    }

    public VKUploadedFile file(String name) {
        if (name == null || multipartFiles == null) {
            return null;
        }
        List<VKUploadedFile> list = multipartFiles.get(name);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public List<VKUploadedFile> files(String name) {
        if (name == null || multipartFiles == null) {
            return List.of();
        }
        List<VKUploadedFile> list = multipartFiles.get(name);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(list);
    }

    public Collection<VKUploadedFile> allFiles() {
        if (allFiles == null || allFiles.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(allFiles);
    }

    public void setParams(Map<String, String> params) {
        this.params = params == null ? new HashMap<>() : params;
    }

    public void applyMultipart(VKMultipartParser.VKMultipartData data) {
        if (data == null) {
            return;
        }
        this.formFields = new HashMap<>(data.fields);
        this.multipartFiles = new HashMap<>(data.files);
        this.allFiles = new ArrayList<>(data.all);
    }

    public void cleanupUploads() {
        if (allFiles == null) {
            return;
        }
        for (VKUploadedFile file : allFiles) {
            file.cleanup();
        }
    }

    private Map<String, String> ensureQueryParams() {
        if (queryParams != null) {
            return queryParams;
        }
        Map<String, String> map = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] parts = query.split("&");
            for (String p : parts) {
                if (p == null || p.isEmpty()) {
                    continue;
                }
                int idx = p.indexOf('=');
                if (idx <= 0) {
                    map.put(decode(p), "");
                } else {
                    String k = decode(p.substring(0, idx));
                    String v = decode(p.substring(idx + 1));
                    map.put(k, v);
                }
            }
        }
        queryParams = map;
        return map;
    }

    private Map<String, String> ensureCookies() {
        if (cookies != null) {
            return cookies;
        }
        Map<String, String> map = new HashMap<>();
        String raw = header("cookie");
        if (raw != null && !raw.isEmpty()) {
            String[] pairs = raw.split(";");
            for (String pair : pairs) {
                if (pair == null || pair.isEmpty()) {
                    continue;
                }
                int idx = pair.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = pair.substring(0, idx).trim();
                String v = pair.substring(idx + 1).trim();
                if (!k.isEmpty()) {
                    map.put(k, v);
                }
            }
        }
        cookies = map;
        return map;
    }

    private String decode(String s) {
        if (s == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
