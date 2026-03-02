package yueyang.vostok.web.websocket;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VKWsRegistry {
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, VKWebSocketSession>> sessionsByPath = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Set<String>>> roomsByPath = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Set<String>>> groupsByPath = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SessionMembership>> membershipsByPath = new ConcurrentHashMap<>();

    public void register(String path, VKWebSocketSession session) {
        String p = normalize(path);
        sessionsByPath.computeIfAbsent(p, k -> new ConcurrentHashMap<>()).put(session.id(), session);
        membershipsByPath.computeIfAbsent(p, k -> new ConcurrentHashMap<>()).put(session.id(), new SessionMembership());
    }

    public void unregister(String path, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String p = normalize(path);
        Map<String, VKWebSocketSession> sessions = sessionsByPath.get(p);
        if (sessions != null) {
            sessions.remove(sessionId);
        }
        Map<String, SessionMembership> members = membershipsByPath.get(p);
        SessionMembership membership = members == null ? null : members.remove(sessionId);
        if (membership == null) {
            return;
        }
        Map<String, Set<String>> roomMap = roomsByPath.get(p);
        for (String room : membership.rooms) {
            removeIndex(roomMap, room, sessionId);
        }
        Map<String, Set<String>> groupMap = groupsByPath.get(p);
        for (String group : membership.groups) {
            removeIndex(groupMap, group, sessionId);
        }
    }

    public boolean joinRoom(String path, String sessionId, String room) {
        if (isBlank(sessionId) || isBlank(room)) {
            return false;
        }
        String p = normalize(path);
        SessionMembership membership = membership(p, sessionId);
        if (membership == null || !membership.rooms.add(room)) {
            return false;
        }
        roomsByPath.computeIfAbsent(p, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        return true;
    }

    public boolean leaveRoom(String path, String sessionId, String room) {
        if (isBlank(sessionId) || isBlank(room)) {
            return false;
        }
        String p = normalize(path);
        SessionMembership membership = membership(p, sessionId);
        if (membership == null || !membership.rooms.remove(room)) {
            return false;
        }
        removeIndex(roomsByPath.get(p), room, sessionId);
        return true;
    }

    public boolean joinGroup(String path, String sessionId, String group) {
        if (isBlank(sessionId) || isBlank(group)) {
            return false;
        }
        String p = normalize(path);
        SessionMembership membership = membership(p, sessionId);
        if (membership == null || !membership.groups.add(group)) {
            return false;
        }
        groupsByPath.computeIfAbsent(p, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(group, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        return true;
    }

    public boolean leaveGroup(String path, String sessionId, String group) {
        if (isBlank(sessionId) || isBlank(group)) {
            return false;
        }
        String p = normalize(path);
        SessionMembership membership = membership(p, sessionId);
        if (membership == null || !membership.groups.remove(group)) {
            return false;
        }
        removeIndex(groupsByPath.get(p), group, sessionId);
        return true;
    }

    public int broadcastAllText(String path, String text) {
        String p = normalize(path);
        Map<String, VKWebSocketSession> sessions = sessionsByPath.get(p);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int sent = 0;
        // Perf2：直接迭代 entrySet 快照，避免创建 Stream 和中间 VKWsCandidate 对象
        for (Map.Entry<String, VKWebSocketSession> e : List.copyOf(sessions.entrySet())) {
            if (sendText(e.getValue(), text)) {
                sent++;
            } else {
                unregister(p, e.getKey());
            }
        }
        return sent;
    }

    public int broadcastAllBinary(String path, byte[] data) {
        String p = normalize(path);
        Map<String, VKWebSocketSession> sessions = sessionsByPath.get(p);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (Map.Entry<String, VKWebSocketSession> e : List.copyOf(sessions.entrySet())) {
            if (sendBinary(e.getValue(), data)) {
                sent++;
            } else {
                unregister(p, e.getKey());
            }
        }
        return sent;
    }

    public int broadcastRoomBinary(String path, String room, byte[] data) {
        if (isBlank(room)) {
            return 0;
        }
        String p = normalize(path);
        Set<String> ids = snapshot(roomsByPath.get(p), room);
        return sendBinaryByIds(p, ids, data);
    }

    public int broadcastGroupBinary(String path, String group, byte[] data) {
        if (isBlank(group)) {
            return 0;
        }
        String p = normalize(path);
        Set<String> ids = snapshot(groupsByPath.get(p), group);
        return sendBinaryByIds(p, ids, data);
    }

    public int broadcastRoomAndGroupBinary(String path, String room, String group, byte[] data) {
        if (isBlank(room) || isBlank(group)) {
            return 0;
        }
        String p = normalize(path);
        Set<String> roomIds = snapshot(roomsByPath.get(p), room);
        Set<String> groupIds = snapshot(groupsByPath.get(p), group);
        if (roomIds.isEmpty() || groupIds.isEmpty()) {
            return 0;
        }
        Set<String> small = roomIds.size() <= groupIds.size() ? roomIds : groupIds;
        Set<String> large = Objects.equals(small, roomIds) ? groupIds : roomIds;
        int sent = 0;
        for (String id : small) {
            if (!large.contains(id)) {
                continue;
            }
            VKWebSocketSession session = session(p, id);
            if (session == null || !session.isOpen()) {
                unregister(p, id);
                continue;
            }
            if (sendBinary(session, data)) {
                sent++;
            } else {
                unregister(p, id);
            }
        }
        return sent;
    }

    public int broadcastRoomText(String path, String room, String text) {
        if (isBlank(room)) {
            return 0;
        }
        String p = normalize(path);
        Set<String> ids = snapshot(roomsByPath.get(p), room);
        return sendByIds(p, ids, text);
    }

    public int broadcastGroupText(String path, String group, String text) {
        if (isBlank(group)) {
            return 0;
        }
        String p = normalize(path);
        Set<String> ids = snapshot(groupsByPath.get(p), group);
        return sendByIds(p, ids, text);
    }

    public int broadcastRoomAndGroupText(String path, String room, String group, String text) {
        if (isBlank(room) || isBlank(group)) {
            return 0;
        }
        String p = normalize(path);
        Set<String> roomIds = snapshot(roomsByPath.get(p), room);
        Set<String> groupIds = snapshot(groupsByPath.get(p), group);
        if (roomIds.isEmpty() || groupIds.isEmpty()) {
            return 0;
        }
        Set<String> small = roomIds.size() <= groupIds.size() ? roomIds : groupIds;
        Set<String> large = Objects.equals(small, roomIds) ? groupIds : roomIds;
        int sent = 0;
        for (String id : small) {
            if (!large.contains(id)) {
                continue;
            }
            VKWebSocketSession session = session(p, id);
            if (session == null || !session.isOpen()) {
                unregister(p, id);
                continue;
            }
            if (sendText(session, text)) {
                sent++;
            } else {
                unregister(p, id);
            }
        }
        return sent;
    }

    private int sendBinaryByIds(String path, Set<String> ids, byte[] data) {
        if (ids.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (String id : ids) {
            VKWebSocketSession session = session(path, id);
            if (session == null || !session.isOpen()) {
                unregister(path, id);
                continue;
            }
            if (sendBinary(session, data)) {
                sent++;
            } else {
                unregister(path, id);
            }
        }
        return sent;
    }

    private int sendByIds(String path, Set<String> ids, String text) {
        if (ids.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (String id : ids) {
            VKWebSocketSession session = session(path, id);
            if (session == null || !session.isOpen()) {
                unregister(path, id);
                continue;
            }
            if (sendText(session, text)) {
                sent++;
            } else {
                unregister(path, id);
            }
        }
        return sent;
    }

    private static boolean sendText(VKWebSocketSession session, String text) {
        try {
            session.sendText(text);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean sendBinary(VKWebSocketSession session, byte[] data) {
        try {
            session.sendBinary(data);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private VKWebSocketSession session(String path, String sessionId) {
        Map<String, VKWebSocketSession> sessions = sessionsByPath.get(path);
        if (sessions == null) {
            return null;
        }
        return sessions.get(sessionId);
    }

    private SessionMembership membership(String path, String sessionId) {
        Map<String, SessionMembership> members = membershipsByPath.get(path);
        if (members == null) {
            return null;
        }
        return members.get(sessionId);
    }

    private static Set<String> snapshot(Map<String, Set<String>> index, String key) {
        if (index == null || key == null) {
            return Set.of();
        }
        Set<String> set = index.get(key);
        if (set == null || set.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(set);
    }

    private static void removeIndex(Map<String, Set<String>> index, String key, String sessionId) {
        if (index == null || key == null || sessionId == null) {
            return;
        }
        Set<String> set = index.get(key);
        if (set == null) {
            return;
        }
        set.remove(sessionId);
        if (set.isEmpty()) {
            index.remove(key, set);
        }
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class SessionMembership {
        private final Set<String> rooms = ConcurrentHashMap.newKeySet();
        private final Set<String> groups = ConcurrentHashMap.newKeySet();
    }

    private record VKWsCandidate(VKWebSocketSession session, String sessionId) {
    }
}
