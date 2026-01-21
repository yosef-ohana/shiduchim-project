package com.example.myproject.service.notification;

import com.example.myproject.model.Notification;
import com.example.myproject.model.NotificationUser;
import com.example.myproject.model.UserDeviceToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpoPushSenderService {

    private static final String EXPO_URL = "https://exp.host/--/api/v2/push/send";

    public enum SendStatus { OK, FAILED, INVALID_TOKEN }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExpoPushSenderService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public SendStatus send(UserDeviceToken deviceToken, NotificationUser nu) {
        if (deviceToken == null || deviceToken.getToken() == null || deviceToken.getToken().isBlank()) {
            return SendStatus.INVALID_TOKEN;
        }
        if (nu == null) return SendStatus.FAILED;

        try {
            Notification n = nu.getNotification();

            String title = (n != null && n.getTitle() != null) ? n.getTitle() : "התראה";
            String body  = (n != null && n.getMessage() != null) ? n.getMessage() : "";

            Map<String, Object> payload = new HashMap<>();
            payload.put("to", deviceToken.getToken().trim());
            payload.put("title", title);
            payload.put("body", body);

            Map<String, Object> data = new HashMap<>();
            data.put("notificationUserId", nu.getId());
            if (n != null) {
                data.put("notificationId", n.getId());
                if (n.getType() != null) data.put("type", String.valueOf(n.getType()));
            }
            payload.put("data", data);

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXPO_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return SendStatus.FAILED;

            String respBody = resp.body() == null ? "" : resp.body().trim();
            if (respBody.isEmpty()) return SendStatus.FAILED;

            // Expo: לרוב {"data":[{"status":"ok"}]} או {"data":[{"status":"error","details":{"error":"DeviceNotRegistered"}}]}
            Map<?, ?> root = objectMapper.readValue(respBody, Map.class);
            Object dataObj = root.get("data");
            if (dataObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object statusObj = first.get("status");
                String status = statusObj != null ? String.valueOf(statusObj) : null;

                if ("ok".equalsIgnoreCase(status)) return SendStatus.OK;

                // error path
                Object detailsObj = first.get("details");
                if (detailsObj instanceof Map<?, ?> details) {
                    Object err = details.get("error");
                    String errStr = err != null ? String.valueOf(err) : "";
                    if (errStr.contains("DeviceNotRegistered") || errStr.contains("NotRegistered")) {
                        return SendStatus.INVALID_TOKEN;
                    }
                }
                return SendStatus.FAILED;
            }

            // fallback (אם פורמט השתנה)
            if (respBody.contains("DeviceNotRegistered") || respBody.contains("NotRegistered")) {
                return SendStatus.INVALID_TOKEN;
            }

            return SendStatus.FAILED;
        } catch (Exception e) {
            return SendStatus.FAILED;
        }
    }
}
