package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.model.Notification;
import com.utn.space.venueaapi.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationStreamService {
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    @Autowired
    private NotificationRepository notificationRepository;

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    ///--------------------------------Metodos-------------------------------------------------------------
    ///
    public SseEmitter subscribe(Integer consumerId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> consumerEmitters =
                emitters.computeIfAbsent(consumerId, id -> new CopyOnWriteArrayList<>());
        consumerEmitters.add(emitter);

        Runnable remove = () -> removeEmitter(consumerId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());

        send(emitter, "connected", Map.of(
                "count", notificationRepository.countByConsumer_IdConsumerAndIsSeenFalse(consumerId)
        ));
        return emitter;
    }

    public void publishNewNotification(Notification notification) {
        Integer consumerId = notification.getConsumer().getIdConsumer();
        long count = notificationRepository.countByConsumer_IdConsumerAndIsSeenFalse(consumerId);
        publish(consumerId, "notification", Map.of(
                "count", count,
                "notification", toStreamDto(notification)
        ));
    }

    public void publishUnreadCount(Integer consumerId) {
        long count = notificationRepository.countByConsumer_IdConsumerAndIsSeenFalse(consumerId);
        publish(consumerId, "unread-count", Map.of("count", count));
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        emitters.forEach((consumerId, consumerEmitters) -> {
            for (SseEmitter emitter : consumerEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception e) {
                    removeEmitter(consumerId, emitter);
                }
            }
        });
    }

    private void publish(Integer consumerId, String eventName, Map<String, Object> data) {
        CopyOnWriteArrayList<SseEmitter> consumerEmitters = emitters.get(consumerId);
        if (consumerEmitters == null) {
            return;
        }
        for (SseEmitter emitter : consumerEmitters) {
            send(emitter, eventName, data);
        }
    }

    private void send(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void removeEmitter(Integer consumerId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> consumerEmitters = emitters.get(consumerId);
        if (consumerEmitters == null) {
            return;
        }
        consumerEmitters.remove(emitter);
        if (consumerEmitters.isEmpty()) {
            emitters.remove(consumerId, consumerEmitters);
        }
    }

    private Map<String, Object> toStreamDto(Notification notification) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("idNotification", notification.getIdNotification());
        dto.put("id", notification.getIdNotification());
        dto.put("message", notification.getMessage());
        dto.put("isSeen", notification.getIsSeen());
        dto.put("seen", notification.getIsSeen());
        dto.put("createdAt", notification.getCreatedAt() == null
                ? null
                : notification.getCreatedAt().atOffset(ZoneOffset.UTC).toInstant().toEpochMilli());
        return dto;
    }
}
