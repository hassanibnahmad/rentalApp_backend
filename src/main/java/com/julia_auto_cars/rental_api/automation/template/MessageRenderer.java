package com.julia_auto_cars.rental_api.automation.template;

import com.julia_auto_cars.rental_api.automation.flow.FlowContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the variables referenced in a template body from a flow context.
 *
 * <p>The renderer uses two sources for variables:</p>
 * <ol>
 *   <li>Aliased variables that the templates reference by short name
 *       ({@code {name}}, {@code {car_name}}, {@code {start_date}}, …). These
 *       are derived from the typed fields of the {@link FlowContext}.</li>
 *   <li>Path-based variables ({@code {user.name}}, {@code {booking.status}}, …)
 *       resolved from the context via dotted-path lookup.</li>
 * </ol>
 */
public final class MessageRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.]+)\\}");

    private MessageRenderer() {}

    public static String render(String templateBody, FlowContext ctx, List<String> requestedPaths) {
        Map<String, String> flat = new HashMap<>();
        flat.putAll(aliasVariables(ctx));
        if (requestedPaths != null) {
            for (String p : requestedPaths) {
                Object v = resolvePath(ctx, p);
                flat.put(p, v == null ? "" : String.valueOf(v));
                // Also expose the last segment under its plain name
                // e.g. "user.name" → "name"
                int dot = p.lastIndexOf('.');
                if (dot > 0 && !flat.containsKey(p.substring(dot + 1))) {
                    flat.put(p.substring(dot + 1), v == null ? "" : String.valueOf(v));
                }
            }
        }
        Matcher m = PLACEHOLDER.matcher(templateBody);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = flat.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, String> aliasVariables(FlowContext ctx) {
        Map<String, String> m = new HashMap<>();
        if (ctx.customer() != null) {
            String fullName = String.join(" ",
                    Objects.toString(ctx.customer().getFirstName(), ""),
                    Objects.toString(ctx.customer().getLastName(), "")).trim();
            m.put("name", fullName.isEmpty() ? "Madame/Monsieur" : fullName);
            m.put("customer_name", fullName.isEmpty() ? "Madame/Monsieur" : fullName);
            m.put("user.name", fullName);
            m.put("user.email", Objects.toString(ctx.customer().getEmail(), ""));
            m.put("user.phone", Objects.toString(ctx.customer().getPhone(), ""));
        }
        if (ctx.car() != null) {
            String carName = (Objects.toString(ctx.car().getBrand(), "") + " " + Objects.toString(ctx.car().getModel(), "")).trim();
            m.put("car_name", carName);
            m.put("car.name", carName);
        }
        if (ctx.reservation() != null) {
            m.put("start_date", formatDate(ctx.reservation().getPickupDate()));
            m.put("end_date", formatDate(ctx.reservation().getReturnDate()));
            m.put("booking.start_date", formatDate(ctx.reservation().getPickupDate()));
            m.put("booking.end_date", formatDate(ctx.reservation().getReturnDate()));
            m.put("pickup_location", Objects.toString(ctx.reservation().getPickupCity(), ""));
            m.put("reservation_number", String.valueOf(ctx.reservation().getId()));
        }
        if (ctx.rental() != null) {
            String startTime = ctx.rental().startTime();
            m.put("start_time", startTime == null ? "" : startTime);
            m.put("pickup_time", startTime == null ? "" : startTime);
            m.put("rental.start_time", startTime == null ? "" : startTime);
        }
        if (ctx.agency() != null) {
            m.put("agency_name", Objects.toString(ctx.agency().getName(), ""));
            m.put("agency_phone", Objects.toString(ctx.agency().getPhone(), ""));
            m.put("location", Objects.toString(ctx.agency().getLocation(), ""));
            m.put("phone", Objects.toString(ctx.agency().getPhone(), ""));
            m.put("agency.location", Objects.toString(ctx.agency().getLocation(), ""));
            m.put("agency.phone", Objects.toString(ctx.agency().getPhone(), ""));
            m.put("review_link", Objects.toString(ctx.agency().getReviewLink(), ""));
            m.put("agency.review_link", Objects.toString(ctx.agency().getReviewLink(), ""));
        }
        m.put("booking_link", Objects.toString(ctx.bookingLink(), ""));
        m.put("booking.link", Objects.toString(ctx.bookingLink(), ""));
        return m;
    }

    private static String formatDate(java.time.LocalDate d) {
        if (d == null) return "";
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    /**
     * Resolve a dotted path like {@code user.name} against the flow context.
     */
    public static Object resolvePath(FlowContext ctx, String path) {
        if (path == null || path.isBlank()) return null;
        String[] parts = path.split("\\.");
        Object current = ctx;
        for (String part : parts) {
            if (current == null) return null;
            current = getField(current, part);
        }
        return current;
    }

    private static Object getField(Object obj, String name) {
        if (obj == null) return null;
        if (obj instanceof Map<?, ?> m) {
            return m.get(name);
        }
        Class<?> clazz = obj.getClass();
        // 1) Try a no-arg method (covers records and bean-style getters
        //    even when the name happens to be a record component).
        try {
            var method = clazz.getMethod(name);
            if (method.getParameterCount() == 0) {
                return method.invoke(obj);
            }
        } catch (ReflectiveOperationException ignored) {
            // No such method; fall through.
        }
        // 2) Try declared field.
        try {
            var field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(obj);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
