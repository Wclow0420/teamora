package com.teamora.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends push notifications via Expo's push service
 * (https://docs.expo.dev/push-notifications/sending-notifications/). Best-effort
 * and off the request thread — failures are logged, never propagated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final PushTokenRepository pushTokens;
    /** Never let a slow Expo endpoint pin the async pool: 5 s to connect, 10 s to answer. */
    static final int CONNECT_TIMEOUT_MS = 5_000;
    static final int READ_TIMEOUT_MS = 10_000;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeouts())
            .build();

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(CONNECT_TIMEOUT_MS);
        f.setReadTimeout(READ_TIMEOUT_MS);
        return f;
    }

    @Async
    @Transactional(readOnly = true)
    public void sendToEmployee(UUID employeeId, String title, String body, String type) {
        List<PushToken> tokens = pushTokens.findByEmployeeId(employeeId);
        if (tokens.isEmpty()) return;

        List<Map<String, Object>> messages = tokens.stream()
                .map(t -> Map.<String, Object>of(
                        "to", t.getToken(),
                        "title", title,
                        "body", body == null ? "" : body,
                        "sound", "default",
                        "data", Map.of("type", type)))
                .toList();
        try {
            restClient.post()
                    .uri(EXPO_PUSH_URL)
                    .header("Content-Type", "application/json")
                    .body(messages)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Expo push send failed for employee {}: {}", employeeId, e.getMessage());
        }
    }
}
