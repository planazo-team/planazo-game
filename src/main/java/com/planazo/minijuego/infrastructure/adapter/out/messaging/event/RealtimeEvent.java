package com.planazo.minijuego.infrastructure.adapter.out.messaging.event;

import java.util.List;
import java.util.UUID;

public record RealtimeEvent(
        String id,
        String type,
        List<String> topics,
        Object payload,
        long at
) {
    public static RealtimeEvent of(String type, List<String> topics, Object payload) {
        return new RealtimeEvent(
                UUID.randomUUID().toString(),
                type,
                topics,
                payload,
                System.currentTimeMillis()
        );
    }
}
