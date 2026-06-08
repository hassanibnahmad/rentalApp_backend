package com.julia_auto_cars.rental_api.automation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound to the {@code automation.*} prefix in application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "automation")
@Getter
@Setter
public class AutomationProperties {

    /** Master switch — when false the engine ignores all incoming events. */
    private boolean enabled = true;

    /** WhatsApp provider settings. */
    private Whatsapp whatsapp = new Whatsapp();

    /** Timing knobs (per spec). */
    private Timing timing = new Timing();

    /** Polling fallback. */
    private Polling polling = new Polling();

    public enum WhatsAppProvider { WPPCONNECT, ULTRAMSG, META }

    @Getter @Setter
    public static class Whatsapp {
        /** Which provider sends the messages. */
        private WhatsAppProvider provider = WhatsAppProvider.ULTRAMSG;

        // ── UltraMsg (paid, ~$10/mo, easiest for non-technical users) ─
        private Ultramsg ultramsg = new Ultramsg();

        // ── WPPConnect (free, self-hosted, no Meta account) ────────────
        private Wppconnect wppconnect = new Wppconnect();

        // ── Meta (optional, official) ────────────────────────────────
        private String token;
        private String phoneNumberId;
        private String apiVersion = "v19.0";
        private String apiBaseUrl = "https://graph.facebook.com";
        private String webhookVerifyToken;
    }

    @Getter @Setter
    public static class Ultramsg {
        /** UltraMsg Instance ID (looks like "instance12345"). */
        private String instanceId;
        /** UltraMsg API token (from the dashboard). */
        private String token;
        /** Base URL. Default is the official UltraMsg endpoint. */
        private String apiBaseUrl = "https://api.ultramsg.com";
    }

    @Getter @Setter
    public static class Wppconnect {
        /** Base URL of the WPPConnect server (no trailing slash). */
        private String baseUrl;
        /** Session name. Use one session per business phone number. */
        private String sessionName = "agency";
        /** Shared secret used to mint JWTs from WPPConnect. Must match the
         *  {@code SECRET_KEY} env var on the WPPConnect server. */
        private String secretKey;
    }

    @Getter @Setter
    public static class Timing {
        private int abandonTimeoutMinutes = 10;
        private int upsellDelayMinutes = 5;
        private int reminderLeadHours = 24;
        private int reviewDelayHours = 24;
        /** Cron-style: how often the rental reminder scanner runs. */
        private long reminderCronIntervalMs = 3_600_000L;
        /** How often the scheduler polls DB for due jobs. */
        private long dispatcherPollMs = 5_000L;
    }

    @Getter @Setter
    public static class Polling {
        private boolean enabled = true;
        private long intervalMs = 30_000L;
    }
}
