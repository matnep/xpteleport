package com.example.xptp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class XpRequestManager {
    private static final Map<UUID, XPRequest> requests = new ConcurrentHashMap<>();

    public static void addRequest(UUID target, UUID requester, int levels) {
        requests.put(target, new XPRequest(requester, levels, System.currentTimeMillis()));
    }

    public static XPRequest getRequest(UUID target) {
        XPRequest request = requests.get(target);
        if (request != null && request.isExpired()) {
            requests.remove(target);
            return null;
        }
        return request;
    }

    public static void removeRequest(UUID target) {
        requests.remove(target);
    }

    public record XPRequest(UUID requester, int levels, long timestamp) {
        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > XptpConfig.getTpaRequestTimeoutSeconds() * 1000L;
        }
    }
}
