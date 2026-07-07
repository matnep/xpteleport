package com.example.xptp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager {
    private static final Map<UUID, TPARequest> requests = new ConcurrentHashMap<>();

    public static void addRequest(UUID target, UUID requester, boolean here) {
        requests.put(target, new TPARequest(requester, here, System.currentTimeMillis()));
    }

    public static TPARequest getRequest(UUID target) {
        TPARequest request = requests.get(target);
        if (request != null && request.isExpired()) {
            requests.remove(target);
            return null;
        }
        return request;
    }

    public static void removeRequest(UUID target) {
        requests.remove(target);
    }

    public record TPARequest(UUID requester, boolean here, long timestamp) {
        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > 60000;
        }
    }
}
