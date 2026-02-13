package yueyang.vostok.web.auto;

import yueyang.vostok.Vostok;
import yueyang.vostok.common.annotation.VKEntity;
import yueyang.vostok.common.json.VKJson;
import yueyang.vostok.common.scan.VKScanner;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class VKAutoCrud {
    private VKAutoCrud() {
    }

    public static List<VKCrudRoute> build(VKCrudStyle style, String... basePackages) {
        Set<Class<?>> classes = VKScanner.scan(basePackages);
        List<VKCrudRoute> routes = new ArrayList<>();
        for (Class<?> clazz : classes) {
            if (!clazz.isAnnotationPresent(VKEntity.class)) {
                continue;
            }
            String basePath = resolvePath(clazz);
            Field idField = resolveIdField(clazz);
            Class<?> idType = idField == null ? String.class : idField.getType();
            if (style == VKCrudStyle.TRADITIONAL) {
                routes.add(new VKCrudRoute("GET", basePath + "/list", listHandler(clazz)));
                routes.add(new VKCrudRoute("GET", basePath + "/get", getHandlerTraditional(clazz, idType)));
                routes.add(new VKCrudRoute("POST", basePath + "/create", insertHandler(clazz)));
                routes.add(new VKCrudRoute("POST", basePath + "/update", updateHandlerTraditional(clazz, idField, idType)));
                routes.add(new VKCrudRoute("POST", basePath + "/delete", deleteHandlerTraditional(clazz, idType)));
            } else {
                routes.add(new VKCrudRoute("GET", basePath, listHandler(clazz)));
                routes.add(new VKCrudRoute("GET", basePath + "/{id}", getHandler(clazz, idType)));
                routes.add(new VKCrudRoute("POST", basePath, insertHandler(clazz)));
                routes.add(new VKCrudRoute("PUT", basePath + "/{id}", updateHandler(clazz, idField, idType)));
                routes.add(new VKCrudRoute("DELETE", basePath + "/{id}", deleteHandler(clazz, idType)));
            }
        }
        return routes;
    }

    private static VKHandler listHandler(Class<?> clazz) {
        return (req, res) -> {
            List<?> all = Vostok.Data.findAll(clazz);
            res.json(VKJson.toJson(all));
        };
    }

    private static VKHandler getHandler(Class<?> clazz, Class<?> idType) {
        return (req, res) -> {
            String idRaw = req.param("id");
            if (idRaw == null) {
                res.status(400).text("Missing id");
                return;
            }
            Object id = parseId(idRaw, idType);
            Object one = Vostok.Data.findById(clazz, id);
            if (one == null) {
                res.status(404).text("Not Found");
                return;
            }
            res.json(VKJson.toJson(one));
        };
    }

    private static VKHandler insertHandler(Class<?> clazz) {
        return (req, res) -> {
            Object entity = parseBody(req, clazz, res);
            if (entity == null) {
                return;
            }
            int inserted = Vostok.Data.insert(entity);
            res.status(201).json("{\"inserted\":" + inserted + "}");
        };
    }

    private static VKHandler updateHandler(Class<?> clazz, Field idField, Class<?> idType) {
        return (req, res) -> {
            Object entity = parseBody(req, clazz, res);
            if (entity == null) {
                return;
            }
            String idRaw = req.param("id");
            if (idRaw != null && idField != null) {
                Object id = parseId(idRaw, idType);
                try {
                    idField.set(entity, id);
                } catch (IllegalAccessException ignore) {
                }
            }
            int updated = Vostok.Data.update(entity);
            res.json("{\"updated\":" + updated + "}");
        };
    }

    private static VKHandler deleteHandler(Class<?> clazz, Class<?> idType) {
        return (req, res) -> {
            String idRaw = req.param("id");
            if (idRaw == null) {
                res.status(400).text("Missing id");
                return;
            }
            Object id = parseId(idRaw, idType);
            int deleted = Vostok.Data.delete(clazz, id);
            res.json("{\"deleted\":" + deleted + "}");
        };
    }

    private static VKHandler getHandlerTraditional(Class<?> clazz, Class<?> idType) {
        return (req, res) -> {
            String idRaw = req.queryParam("id");
            if (idRaw == null || idRaw.isEmpty()) {
                res.status(400).text("Missing id");
                return;
            }
            Object id = parseId(idRaw, idType);
            Object one = Vostok.Data.findById(clazz, id);
            if (one == null) {
                res.status(404).text("Not Found");
                return;
            }
            res.json(VKJson.toJson(one));
        };
    }

    private static VKHandler updateHandlerTraditional(Class<?> clazz, Field idField, Class<?> idType) {
        return (req, res) -> {
            Object entity = parseBody(req, clazz, res);
            if (entity == null) {
                return;
            }
            String idRaw = req.queryParam("id");
            if (idRaw != null && idField != null) {
                Object id = parseId(idRaw, idType);
                try {
                    idField.set(entity, id);
                } catch (IllegalAccessException ignore) {
                }
            }
            int updated = Vostok.Data.update(entity);
            res.json("{\"updated\":" + updated + "}");
        };
    }

    private static VKHandler deleteHandlerTraditional(Class<?> clazz, Class<?> idType) {
        return (req, res) -> {
            String idRaw = req.queryParam("id");
            if (idRaw == null || idRaw.isEmpty()) {
                res.status(400).text("Missing id");
                return;
            }
            Object id = parseId(idRaw, idType);
            int deleted = Vostok.Data.delete(clazz, id);
            res.json("{\"deleted\":" + deleted + "}");
        };
    }

    private static Object parseBody(VKRequest req, Class<?> clazz, VKResponse res) {
        String body = req.bodyText();
        if (body == null || body.isEmpty()) {
            res.status(400).text("Empty body");
            return null;
        }
        try {
            return VKJson.fromJson(body, clazz);
        } catch (Exception e) {
            res.status(400).text("Invalid JSON");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object parseId(String raw, Class<?> type) {
        if (type == String.class) {
            return raw;
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(raw);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(raw);
        }
        if (type == short.class || type == Short.class) {
            return Short.parseShort(raw);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.parseByte(raw);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(raw);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(raw);
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, raw);
        }
        return raw;
    }

    private static Field resolveIdField(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        Field candidate = null;
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.isAnnotationPresent(VKId.class)) {
                f.setAccessible(true);
                return f;
            }
            if ("id".equalsIgnoreCase(f.getName())) {
                candidate = f;
            }
        }
        if (candidate != null) {
            candidate.setAccessible(true);
        }
        return candidate;
    }

    private static String resolvePath(Class<?> clazz) {
        VKEntity entity = clazz.getAnnotation(VKEntity.class);
        if (entity != null) {
            String path = entity.path();
            if (path != null && !path.trim().isEmpty()) {
                String p = path.trim();
                return p.startsWith("/") ? p : "/" + p;
            }
        }
        String name = clazz.getSimpleName();
        if (name.endsWith("Entity") && name.length() > 6) {
            name = name.substring(0, name.length() - 6);
        }
        if (name.isEmpty()) {
            name = "entity";
        }
        String lower = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return "/" + lower;
    }

    public record VKCrudRoute(String method, String path, VKHandler handler) {
    }
}
