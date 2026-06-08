package com.julia_auto_cars.rental_api.automation.action;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Discriminated union of all action types. Exactly mirrors the spec.
 *
 * <p>We use sealed interfaces + records so Jackson can deserialize the JSON
 * shape {@code {"type": "...", ...}} cleanly.</p>
 */
public sealed interface FlowAction
        permits FlowAction.Delay, FlowAction.Condition, FlowAction.SendWhatsApp,
        FlowAction.UpdateFlag, FlowAction.CronCheck {

    String type();

    @JsonCreator
    static FlowAction fromJson(String type, Object raw) {
        return switch (type) {
            case "delay"          -> Delay.fromRaw(raw);
            case "condition"      -> Condition.fromRaw(raw);
            case "send_whatsapp"  -> SendWhatsApp.fromRaw(raw);
            case "update_flag"    -> UpdateFlag.fromRaw(raw);
            case "cron_check"     -> CronCheck.fromRaw(raw);
            default -> throw new IllegalArgumentException("Unknown action type: " + type);
        };
    }

    record Delay(@JsonProperty("duration") String duration) implements FlowAction {
        public String type() { return "delay"; }
        static Delay fromRaw(Object raw) {
            if (raw instanceof Delay d) return d;
            if (raw instanceof java.util.Map<?, ?> m) return new Delay(String.valueOf(m.get("duration")));
            return new Delay("");
        }
    }

    record Condition(@JsonProperty("expression") String expression) implements FlowAction {
        public String type() { return "condition"; }
        static Condition fromRaw(Object raw) {
            if (raw instanceof Condition c) return c;
            if (raw instanceof java.util.Map<?, ?> m) return new Condition(String.valueOf(m.get("expression")));
            return new Condition("");
        }
    }

    record SendWhatsApp(
            @JsonProperty("template_id") String templateId,
            @JsonProperty("variables") List<String> variables
    ) implements FlowAction {
        public String type() { return "send_whatsapp"; }
        static SendWhatsApp fromRaw(Object raw) {
            if (raw instanceof SendWhatsApp s) return s;
            if (raw instanceof java.util.Map<?, ?> m) {
                Object vars = m.get("variables");
                @SuppressWarnings("unchecked")
                List<String> list = vars instanceof List<?> l
                        ? l.stream().map(String::valueOf).toList()
                        : List.of();
                return new SendWhatsApp(String.valueOf(m.get("template_id")), list);
            }
            return new SendWhatsApp("", List.of());
        }
    }

    record UpdateFlag(
            @JsonProperty("field") String field,
            @JsonProperty("value") Object value
    ) implements FlowAction {
        public String type() { return "update_flag"; }
        static UpdateFlag fromRaw(Object raw) {
            if (raw instanceof UpdateFlag u) return u;
            if (raw instanceof java.util.Map<?, ?> m) return new UpdateFlag(String.valueOf(m.get("field")), m.get("value"));
            return new UpdateFlag("", null);
        }
    }

    record CronCheck(@JsonProperty("interval") String interval) implements FlowAction {
        public String type() { return "cron_check"; }
        static CronCheck fromRaw(Object raw) {
            if (raw instanceof CronCheck c) return c;
            if (raw instanceof java.util.Map<?, ?> m) return new CronCheck(String.valueOf(m.get("interval")));
            return new CronCheck("");
        }
    }
}
