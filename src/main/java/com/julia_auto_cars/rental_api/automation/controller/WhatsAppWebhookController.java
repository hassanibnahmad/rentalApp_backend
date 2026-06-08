package com.julia_auto_cars.rental_api.automation.controller;

import com.julia_auto_cars.rental_api.automation.config.AutomationProperties;
import com.julia_auto_cars.rental_api.automation.model.MessageStatus;
import com.julia_auto_cars.rental_api.automation.model.WhatsAppMessage;
import com.julia_auto_cars.rental_api.automation.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Meta WhatsApp webhook receiver.
 *
 * <ul>
 *   <li>{@code GET /api/automation/webhooks/whatsapp} — used by Meta to
 *       verify the endpoint during setup.</li>
 *   <li>{@code POST /api/automation/webhooks/whatsapp} — delivery-status
 *       callbacks. We update the matching {@link WhatsAppMessage} row by
 *       provider message id.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/automation/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final AutomationProperties properties;
    private final WhatsAppMessageRepository messageRepository;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && token.equals(properties.getWhatsapp().getWebhookVerifyToken())) {
            log.info("whatsapp_webhook_verified");
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).body("verification_failed");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(@RequestBody Map<String, Object> body) {
        if (!"whatsapp_business_account".equals(body.get("object"))) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", true));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.getOrDefault("entry", List.of());
        for (Map<String, Object> entry : entries) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.getOrDefault("changes", List.of());
            for (Map<String, Object> change : changes) {
                @SuppressWarnings("unchecked")
                Map<String, Object> value = (Map<String, Object>) change.get("value");
                if (value == null) continue;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.getOrDefault("statuses", List.of());
                for (Map<String, Object> s : statuses) {
                    String id = String.valueOf(s.get("id"));
                    String statusStr = String.valueOf(s.get("status"));
                    Optional<WhatsAppMessage> opt = messageRepository.findFirstByProviderMessageId(id);
                    if (opt.isEmpty()) continue;
                    WhatsAppMessage msg = opt.get();
                    MessageStatus newStatus = mapStatus(statusStr);
                    msg.setStatus(newStatus);
                    if (newStatus == MessageStatus.DELIVERED) {
                        msg.setDeliveredAt(OffsetDateTime.now());
                    } else if (newStatus == MessageStatus.READ) {
                        msg.setReadAt(OffsetDateTime.now());
                    }
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> errs = (List<Map<String, Object>>) s.get("errors");
                    if (errs != null && !errs.isEmpty()) {
                        Map<String, Object> e = errs.get(0);
                        msg.setProviderError(e.get("code") + ": " + e.get("title"));
                    }
                    messageRepository.save(msg);
                    log.info("whatsapp_status_updated id={} status={}", id, newStatus);
                }
            }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private MessageStatus mapStatus(String s) {
        if (s == null) return MessageStatus.SENT;
        return switch (s) {
            case "sent"      -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read"      -> MessageStatus.READ;
            case "failed"    -> MessageStatus.FAILED;
            default          -> MessageStatus.SENT;
        };
    }
}
