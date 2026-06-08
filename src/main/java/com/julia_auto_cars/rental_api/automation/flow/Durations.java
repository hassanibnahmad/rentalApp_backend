package com.julia_auto_cars.rental_api.automation.flow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse a duration string like {@code "10m"}, {@code "24h"}, {@code "7d"} into milliseconds.
 */
public final class Durations {

    private static final Pattern PATTERN = Pattern.compile("^\\s*(\\d+)\\s*(ms|s|m|h|d)\\s*$");

    private Durations() {}

    public static long toMillis(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration is empty");
        }
        Matcher m = PATTERN.matcher(input);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        long value = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "ms" -> value;
            case "s"  -> value * 1_000L;
            case "m"  -> value * 60_000L;
            case "h"  -> value * 3_600_000L;
            case "d"  -> value * 86_400_000L;
            default   -> throw new IllegalArgumentException("Unknown unit in: " + input);
        };
    }
}
