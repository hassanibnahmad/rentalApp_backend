package com.julia_auto_cars.rental_api.automation.condition;

import com.julia_auto_cars.rental_api.automation.flow.FlowContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe mini expression language for flow conditions.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Dotted paths: {@code user.name}, {@code booking.status}</li>
 *   <li>Strict equality: {@code ===}, {@code !==}</li>
 *   <li>Comparisons: {@code <}, {@code >}, {@code <=}, {@code >=}</li>
 *   <li>Logical: {@code &&}, {@code ||}, {@code !}</li>
 *   <li>Grouping: {@code ( ... )}</li>
 *   <li>String literals: {@code 'foo'}, {@code "foo"}</li>
 *   <li>Number literals: {@code 42}, {@code 3.14}</li>
 *   <li>Function calls: {@code differenceInHours(a, b)},
 *       {@code differenceInMinutes(a, b)}</li>
 *   <li>Keywords: {@code now}, {@code true}, {@code false}, {@code null}</li>
 * </ul>
 *
 * <p>This is intentionally a tiny hand-written parser rather than a full
 * expression engine — we want zero attack surface and zero classpath
 * surprises.</p>
 *
 * <p>Every condition in the spec parses with this engine:</p>
 * <pre>
 *   booking.status === 'pending' && !booking.abandoned_sent
 *   !booking.confirmation_sent
 *   differenceInHours(rental.start_date, now) <= 24 && !rental.reminder_sent
 *   rental.completed === true && !rental.review_sent
 * </pre>
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    public static boolean evaluate(String expression, FlowContext ctx) {
        if (expression == null || expression.isBlank()) return true;
        List<Token> tokens = tokenize(expression);
        Parser p = new Parser(tokens, ctx);
        Object result = p.parseExpression();
        p.expectEnd();
        return toBoolean(result);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tokenizer
    // ─────────────────────────────────────────────────────────────────────
    private enum TKind { IDENT, STRING, NUMBER, OP, PUNCT }
    private record Token(TKind kind, String value, Object literal) {
        static Token op(String v)        { return new Token(TKind.OP, v, null); }
        static Token punct(String v)     { return new Token(TKind.PUNCT, v, null); }
        static Token ident(String v)     { return new Token(TKind.IDENT, v, null); }
        static Token str(String v)       { return new Token(TKind.STRING, v, v); }
        static Token num(double v)       { return new Token(TKind.NUMBER, String.valueOf(v), v); }
    }

    private static List<Token> tokenize(String src) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '(' || c == ')' || c == ',') {
                out.add(Token.punct(String.valueOf(c)));
                i++;
                continue;
            }
            if (c == '!' && i + 1 < src.length() && src.charAt(i + 1) == '=') {
                out.add(Token.op("!=="));
                i += 2;
                continue;
            }
            if (c == '=' && i + 2 < src.length() && src.charAt(i + 1) == '=' && src.charAt(i + 2) == '=') {
                out.add(Token.op("==="));
                i += 3;
                continue;
            }
            if (c == '<' && i + 1 < src.length() && src.charAt(i + 1) == '=') {
                out.add(Token.op("<="));
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < src.length() && src.charAt(i + 1) == '=') {
                out.add(Token.op(">="));
                i += 2;
                continue;
            }
            if (c == '<') { out.add(Token.op("<")); i++; continue; }
            if (c == '>') { out.add(Token.op(">")); i++; continue; }
            if (c == '!') { out.add(Token.punct("!")); i++; continue; }
            if (c == '&' && i + 1 < src.length() && src.charAt(i + 1) == '&') {
                out.add(Token.punct("&&"));
                i += 2;
                continue;
            }
            if (c == '|' && i + 1 < src.length() && src.charAt(i + 1) == '|') {
                out.add(Token.punct("||"));
                i += 2;
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                int j = i + 1;
                StringBuilder buf = new StringBuilder();
                while (j < src.length() && src.charAt(j) != quote) {
                    if (src.charAt(j) == '\\' && j + 1 < src.length()) {
                        buf.append(src.charAt(j + 1));
                        j += 2;
                    } else {
                        buf.append(src.charAt(j));
                        j++;
                    }
                }
                if (j >= src.length()) throw new IllegalArgumentException("Unterminated string at " + i);
                out.add(Token.str(buf.toString()));
                i = j + 1;
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1)))) {
                int j = i;
                StringBuilder buf = new StringBuilder();
                if (c == '-') { buf.append('-'); j++; }
                while (j < src.length() && (Character.isDigit(src.charAt(j)) || src.charAt(j) == '.')) {
                    buf.append(src.charAt(j));
                    j++;
                }
                out.add(Token.num(Double.parseDouble(buf.toString())));
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                StringBuilder buf = new StringBuilder();
                while (j < src.length() && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_' || src.charAt(j) == '.')) {
                    buf.append(src.charAt(j));
                    j++;
                }
                out.add(Token.ident(buf.toString()));
                i = j;
                continue;
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + i);
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Parser
    // ─────────────────────────────────────────────────────────────────────
    private static final class Parser {
        private final List<Token> tokens;
        private final FlowContext ctx;
        private int pos = 0;

        Parser(List<Token> tokens, FlowContext ctx) {
            this.tokens = tokens;
            this.ctx = ctx;
        }

        Object parseExpression() { return parseOr(); }

        private Object parseOr() {
            Object left = parseAnd();
            while (peekPunct("||")) {
                pos++;
                Object right = parseAnd();
                left = toBoolean(left) || toBoolean(right);
            }
            return left;
        }

        private Object parseAnd() {
            Object left = parseNot();
            while (peekPunct("&&")) {
                pos++;
                Object right = parseNot();
                left = toBoolean(left) && toBoolean(right);
            }
            return left;
        }

        private Object parseNot() {
            if (peekPunct("!")) {
                pos++;
                return !toBoolean(parseNot());
            }
            return parseComparison();
        }

        private Object parseComparison() {
            Object left = parsePrimary();
            if (pos < tokens.size() && tokens.get(pos).kind == TKind.OP) {
                String op = tokens.get(pos).value;
                pos++;
                Object right = parsePrimary();
                return switch (op) {
                    case "===" -> Objects.equals(coerce(left), coerce(right));
                    case "!==" -> !Objects.equals(coerce(left), coerce(right));
                    case "<="  -> compare(left, right) <= 0;
                    case ">="  -> compare(left, right) >= 0;
                    case "<"   -> compare(left, right) < 0;
                    case ">"   -> compare(left, right) > 0;
                    default    -> throw new IllegalStateException("Unknown operator " + op);
                };
            }
            return left;
        }

        private Object parsePrimary() {
            Token t = tokens.get(pos);
            if (t.kind == TKind.PUNCT && t.value.equals("(")) {
                pos++;
                Object v = parseExpression();
                Token close = tokens.get(pos);
                if (close.kind != TKind.PUNCT || !close.value.equals(")")) {
                    throw new IllegalArgumentException("Expected ')'");
                }
                pos++;
                return v;
            }
            if (t.kind == TKind.STRING || t.kind == TKind.NUMBER) {
                pos++;
                return t.literal;
            }
            if (t.kind == TKind.IDENT) {
                pos++;
                String name = t.value;
                Object resolved = resolveIdent(name);
                // Function call?
                if (pos < tokens.size()
                        && tokens.get(pos).kind == TKind.PUNCT
                        && tokens.get(pos).value.equals("(")) {
                    if (!(resolved instanceof Builtin)) {
                        throw new IllegalArgumentException("\"" + name + "\" is not a function");
                    }
                    pos++; // consume "("
                    List<Object> args = new ArrayList<>();
                    if (!(pos < tokens.size()
                            && tokens.get(pos).kind == TKind.PUNCT
                            && tokens.get(pos).value.equals(")"))) {
                        args.add(parseExpression());
                        while (pos < tokens.size()
                                && tokens.get(pos).kind == TKind.PUNCT
                                && tokens.get(pos).value.equals(",")) {
                            pos++;
                            args.add(parseExpression());
                        }
                    }
                    Token close = tokens.get(pos);
                    if (close.kind != TKind.PUNCT || !close.value.equals(")")) {
                        throw new IllegalArgumentException("Expected ')' after function args");
                    }
                    pos++;
                    return ((Builtin) resolved).apply(args);
                }
                return resolved;
            }
            throw new IllegalArgumentException("Unexpected token: " + t.value);
        }

        private boolean peekPunct(String v) {
            return pos < tokens.size()
                    && tokens.get(pos).kind == TKind.PUNCT
                    && tokens.get(pos).value.equals(v);
        }

        void expectEnd() {
            if (pos < tokens.size()) {
                throw new IllegalArgumentException("Trailing tokens: " + tokens.get(pos).value);
            }
        }

        // ─── identifier resolution ────────────────────────────────────────
        private Object resolveIdent(String name) {
            if (name.equals("true"))  return Boolean.TRUE;
            if (name.equals("false")) return Boolean.FALSE;
            if (name.equals("null"))  return null;
            if (name.equals("now"))   return OffsetDateTime.now(ZoneOffset.UTC);
            if (name.equals("differenceInHours"))   return Builtin.DIFF_HOURS;
            if (name.equals("differenceInMinutes")) return Builtin.DIFF_MINUTES;
            return com.julia_auto_cars.rental_api.automation.template.MessageRenderer.resolvePath(ctx, name);
        }
    }

    @FunctionalInterface
    private interface Builtin {
        Object apply(List<Object> args);
        Builtin DIFF_HOURS   = args -> {
            OffsetDateTime a = asDateTime(args.get(0));
            OffsetDateTime b = asDateTime(args.get(1));
            return (double) ChronoUnit.MINUTES.between(b, a) / 60.0;
        };
        Builtin DIFF_MINUTES = args -> {
            OffsetDateTime a = asDateTime(args.get(0));
            OffsetDateTime b = asDateTime(args.get(1));
            return (double) ChronoUnit.MINUTES.between(b, a);
        };
    }

    private static OffsetDateTime asDateTime(Object v) {
        if (v == null) return OffsetDateTime.now(ZoneOffset.UTC);
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof LocalDate d) return d.atStartOfDay().atOffset(ZoneOffset.UTC);
        if (v instanceof Number) return OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(((Number) v).longValue());
        if (v instanceof String s) {
            if ("now".equals(s)) return OffsetDateTime.now(ZoneOffset.UTC);
            try { return OffsetDateTime.parse(s); } catch (Exception ignored) {}
            try { return LocalDate.parse(s).atStartOfDay().atOffset(ZoneOffset.UTC); } catch (Exception ignored) {}
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (v instanceof Map<?, ?> m) {
            Object start = m.get("start_date");
            if (start instanceof LocalDate d) return d.atStartOfDay().atOffset(ZoneOffset.UTC);
            if (start instanceof OffsetDateTime o) return o;
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        OffsetDateTime aa = asDateTime(a);
        OffsetDateTime bb = asDateTime(b);
        return aa.compareTo(bb);
    }

    /** Coerce a value so equality compares like-typed values. */
    private static Object coerce(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) {
            // Promote everything to Double so === works for integer literals.
            return n.doubleValue();
        }
        if (v instanceof Enum<?> e) return e.name().toLowerCase();
        return v;
    }

    private static boolean toBoolean(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s)  return !s.isEmpty() && !s.equalsIgnoreCase("false");
        if (v instanceof Enum<?> e) return true;
        return true;
    }
}
