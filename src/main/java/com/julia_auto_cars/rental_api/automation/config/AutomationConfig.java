package com.julia_auto_cars.rental_api.automation.config;

import com.julia_auto_cars.rental_api.automation.flow.FlowRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Top-level config for the automation module:
 * <ul>
 *   <li>Enable {@code @Scheduled} (cron, dispatcher, poller).</li>
 *   <li>Enable {@code @Async}.</li>
 *   <li>Pin the JVM default timezone to Africa/Casablanca so any code that
 *       uses {@code new Date()} or {@code OffsetDateTime.now()} without an
 *       explicit zone still respects the spec.</li>
 *   <li>Build the {@link FlowRegistry} bean with values from
 *       {@link AutomationProperties}.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableAsync
@RequiredArgsConstructor
public class AutomationConfig {

    private final AutomationProperties properties;

    static {
        // Pin the JVM default TZ. Spring uses this for @Scheduled cron
        // expressions and Jackson uses it for OffsetDateTime serialisation.
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Casablanca"));
    }

    @Bean
    public FlowRegistry flowRegistry() {
        var t = properties.getTiming();
        return new FlowRegistry(
                t.getAbandonTimeoutMinutes(),
                t.getUpsellDelayMinutes(),
                t.getReviewDelayHours(),
                t.getReminderCronIntervalMs()
        );
    }
}
