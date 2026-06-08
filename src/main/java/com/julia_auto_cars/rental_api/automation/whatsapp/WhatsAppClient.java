package com.julia_auto_cars.rental_api.automation.whatsapp;

import com.julia_auto_cars.rental_api.automation.config.AutomationProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * WhatsApp gateway. Two providers are supported, selected at runtime by the
 * {@code automation.whatsapp.provider} property:
 *
 * <ul>
 *   <li>{@code wppconnect} (default, free) — talks to a self-hosted
 *       <a href="https://github.com/wppconnect-team/wppconnect-server">wppconnect-server</a>
 *       instance. No Meta account needed. The server is deployed alongside the
 *       backend and authenticated by a shared secret.</li>
 *   <li>{@code meta} — talks to Meta's official Cloud API.</li>
 * </ul>
 *
 * <p>The rest of the automation module is provider-agnostic.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppClient {

    private final AutomationProperties properties;

    @PostConstruct
    void announceProvider() {
        log.info("whatsapp_provider_active provider={}", properties.getWhatsapp().getProvider());
    }

    public SendResult sendText(String to, String body) {
        return switch (properties.getWhatsapp().getProvider()) {
            case ULTRAMSG   -> sendViaUltraMsg(to, body);
            case WPPCONNECT -> sendViaWppConnect(to, body);
            case META       -> sendViaMeta(to, body);
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // UltraMsg (https://ultramsg.com) — paid but very simple, with web UI
    // ─────────────────────────────────────────────────────────────────────

    private SendResult sendViaUltraMsg(String to, String body) {
        var cfg = properties.getWhatsapp().getUltramsg();
        if (cfg.getInstanceId() == null || cfg.getInstanceId().isBlank()
                || cfg.getToken() == null || cfg.getToken().isBlank()) {
            return new SendResult(false, null,
                    "ULTRAMSG_INSTANCE_ID and ULTRAMSG_TOKEN must be configured", 0);
        }
        String normalizedPhone = to.replaceAll("[^0-9]", "");
        // UltraMsg auth: token as a query string parameter.
        String url = String.format("%s/%s/messages/chat?token=%s",
                properties.getWhatsapp().getUltramsg().getApiBaseUrl(),
                cfg.getInstanceId(),
                cfg.getToken());
        Map<String, Object> payload = Map.of("to", normalizedPhone, "body", body);
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(properties.getWhatsapp().getUltramsg().getApiBaseUrl())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/{instance}/messages/chat?token={token}",
                            cfg.getInstanceId(), cfg.getToken())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return new SendResult(false, null, "Empty response from UltraMsg", 502);
            }
            // UltraMsg returns {"sent":true,"id":"...","message":"..."} on success.
            Object sent = response.get("sent");
            if (sent != null && "false".equalsIgnoreCase(String.valueOf(sent))) {
                String err = String.valueOf(response.getOrDefault("error", response.get("message")));
                return new SendResult(false, null, "UltraMsg rejected: " + err, 502);
            }
            String providerMessageId = String.valueOf(response.getOrDefault("id", response.get("messageId")));
            log.info("ultramsg_sent to={} providerMessageId={}", normalizedPhone, providerMessageId);
            return new SendResult(true, providerMessageId, null, 200);
        } catch (RestClientResponseException ex) {
            String body0 = ex.getResponseBodyAsString();
            log.error("ultramsg_send_failed status={} to={} body={}", ex.getStatusCode(), normalizedPhone, body0);
            return new SendResult(false, null, "HTTP " + ex.getStatusCode().value() + ": " + truncate(body0, 500), ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("ultramsg_send_exception to={}", normalizedPhone, ex);
            return new SendResult(false, null, ex.getMessage(), 0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // WPPConnect
    // ─────────────────────────────────────────────────────────────────────

    private WppAuth wppAuth;

    private synchronized WppAuth wppAuth() {
        if (wppAuth == null) {
            wppAuth = new WppAuth(properties.getWhatsapp().getWppconnect().getBaseUrl(),
                    properties.getWhatsapp().getWppconnect().getSecretKey());
        }
        return wppAuth;
    }

    private SendResult sendViaWppConnect(String to, String body) {
        var cfg = properties.getWhatsapp().getWppconnect();
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            return new SendResult(false, null, "WPPCONNECT_BASE_URL is not configured", 0);
        }
        if (cfg.getSessionName() == null || cfg.getSessionName().isBlank()) {
            return new SendResult(false, null, "WPPCONNECT_SESSION is not configured", 0);
        }
        String normalizedPhone = to.replaceAll("[^0-9]", "");
        String url = String.format("%s/api/%s/send-message",
                trimTrailingSlash(cfg.getBaseUrl()), cfg.getSessionName());
        Map<String, Object> payload = Map.of(
                "phone", normalizedPhone,
                "message", body,
                "isGroup", false
        );
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(cfg.getBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + wppAuth().token())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/api/{session}/send-message", cfg.getSessionName())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return new SendResult(false, null, "Empty response from WPPConnect", 502);
            }
            Object status = response.get("status");
            if (status != null && !"success".equalsIgnoreCase(String.valueOf(status))) {
                String err = String.valueOf(response.getOrDefault("message", response.get("error")));
                return new SendResult(false, null, "WPPConnect rejected: " + err, 502);
            }
            String providerMessageId = String.valueOf(response.getOrDefault("id", response.get("messageId")));
            log.info("wppconnect_sent to={} providerMessageId={}", normalizedPhone, providerMessageId);
            return new SendResult(true, providerMessageId, null, 200);
        } catch (RestClientResponseException ex) {
            String body0 = ex.getResponseBodyAsString();
            // Token expired? Try once more after refreshing.
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                log.warn("wppconnect_auth_retry status={}", ex.getStatusCode());
                wppAuth = null;
                return sendViaWppConnect(to, body);
            }
            log.error("wppconnect_send_failed status={} to={} body={}", ex.getStatusCode(), normalizedPhone, body0);
            return new SendResult(false, null, "HTTP " + ex.getStatusCode().value() + ": " + truncate(body0, 500), ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("wppconnect_send_exception to={}", normalizedPhone, ex);
            return new SendResult(false, null, ex.getMessage(), 0);
        }
    }

    /**
     * Fetches (and caches) a JWT from the WPPConnect server using the shared
     * secret. The token is reused for subsequent calls and refreshed on 401/403.
     */
    private record WppAuth(String baseUrl, String secretKey) {
        private static volatile String cached;

        String token() {
            String current = cached;
            if (current != null && !current.isBlank()) {
                return current;
            }
            synchronized (WppAuth.class) {
                if (cached == null || cached.isBlank()) {
                    cached = fetchToken();
                }
                return cached;
            }
        }

        @SuppressWarnings("unchecked")
        private String fetchToken() {
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            Map<String, Object> response = client.post()
                    .uri("/api/{session}/generate-token", "default")
                    .body(Map.of("sessionName", "default"))
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new IllegalStateException("Empty response while generating WPPConnect token");
            }
            String token = String.valueOf(response.get("token"));
            if ("null".equals(token) || token.isBlank()) {
                throw new IllegalStateException("WPPConnect did not return a token: " + response);
            }
            return token;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Meta (kept for future migration)
    // ─────────────────────────────────────────────────────────────────────

    private SendResult sendViaMeta(String to, String body) {
        if (properties.getWhatsapp().getToken() == null || properties.getWhatsapp().getToken().isBlank()) {
            log.warn("whatsapp_token_missing_skipping sendTo={}", to);
            return new SendResult(false, null, "WHATSAPP_TOKEN is not configured", 0);
        }
        String phoneNumberId = properties.getWhatsapp().getPhoneNumberId();
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("body", body)
        );
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(properties.getWhatsapp().getApiBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getWhatsapp().getToken())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/{version}/{phoneId}/messages",
                            properties.getWhatsapp().getApiVersion(), phoneNumberId)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return new SendResult(false, null, "Empty response from Meta", 502);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) response.get("messages");
            if (messages == null || messages.isEmpty()) {
                return new SendResult(false, null, "Meta response missing 'messages'", 502);
            }
            String providerMessageId = String.valueOf(messages.get(0).get("id"));
            log.info("whatsapp_sent to={} providerMessageId={}", to, providerMessageId);
            return new SendResult(true, providerMessageId, null, 200);
        } catch (RestClientResponseException ex) {
            String body0 = ex.getResponseBodyAsString();
            log.error("whatsapp_send_failed status={} to={} body={}", ex.getStatusCode(), to, body0);
            return new SendResult(false, null, "HTTP " + ex.getStatusCode().value() + ": " + truncate(body0, 500), ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("whatsapp_send_exception to={}", to, ex);
            return new SendResult(false, null, ex.getMessage(), 0);
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record SendResult(boolean ok, String providerMessageId, String error, int statusCode) {}
}
