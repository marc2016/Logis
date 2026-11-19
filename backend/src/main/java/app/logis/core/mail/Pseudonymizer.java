package app.logis.core.mail;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces personal values in mail text for one admin export (design.md of admin-mail-export, D5). The same original
 * value always gets the same replacement within one instance. Best effort: values it does not know and that match
 * no pattern stay.
 */
class Pseudonymizer {

    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@((?:[\\p{L}\\p{N}-]+\\.)+[\\p{L}]{2,})");
    /** A leading + or 0, then digits with spaces, slashes, dashes or parentheses; at least 7 digits (checked below). */
    private static final Pattern PHONE = Pattern.compile("(?<![\\p{L}\\p{N}.,])(?:\\+|0)[\\d (/)\\-]{5,}\\d(?![\\p{N}.,]\\d)");
    private static final int MIN_NAME_PART = 3;
    private static final String EXAMPLE_DOMAIN = "example.org";

    private final Random random;
    /** Replacements per kind and original value. */
    private final Map<SensitiveValue.Kind, Map<String, String>> known = new HashMap<>();
    private final Map<String, String> patternEmails = new HashMap<>();
    private final Map<String, String> sameFormat = new HashMap<>();
    /** Every value this instance produced, so later patterns leave it alone. */
    private final Set<String> produced = new HashSet<>();

    Pseudonymizer(Random random) {
        this.random = random;
    }

    /**
     * Pseudonymizes {@code text}. {@code keepDomains}: e-mail domains that stay, such as the agency's sender domain;
     * {@code html}: the text is HTML, so escaped forms of the values are replaced too.
     */
    String apply(String text, Collection<SensitiveValue> values, Set<String> keepDomains, boolean html) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        var result = text;
        for (var pair : pairs(values, html)) {
            result = replaceWord(result, pair[0], pair[1]);
        }
        result = replaceAll(result, IBAN, m -> m.group().substring(0, 2) + sameFormat(m.group().substring(2)));
        result = replaceAll(result, EMAIL, m -> email(m.group(), m.group(1), keepDomains));
        result = replaceAll(result, PHONE, m -> digits(m.group()) >= 7 ? sameFormat(m.group()) : m.group());
        return result;
    }

    /** An address in a header: the address part only. */
    String address(String address, Collection<SensitiveValue> values, Set<String> keepDomains) {
        if (address == null) {
            return null;
        }
        for (var v : values) {
            if (v.kind() == SensitiveValue.Kind.EMAIL && v.value().equalsIgnoreCase(address)) {
                return replacement(v);
            }
        }
        var at = address.lastIndexOf('@');
        return at < 0 ? address : email(address, address.substring(at + 1), keepDomains);
    }

    /** Pairs (original, replacement), longest original first, so that a full name wins over its parts. */
    private List<String[]> pairs(Collection<SensitiveValue> values, boolean html) {
        var pairs = new ArrayList<String[]>();
        for (var v : values) {
            var replacement = replacement(v);
            pairs.add(new String[] {v.value(), replacement});
            if (v.kind() == SensitiveValue.Kind.NAME) {
                for (var part : v.value().split("[\\s,]+")) {
                    if (part.codePointCount(0, part.length()) >= MIN_NAME_PART && !part.equals(v.value())) {
                        pairs.add(new String[] {part, replacement.replace(" ", "")});
                    }
                }
            }
        }
        if (html) {
            for (var pair : List.copyOf(pairs)) {
                var escaped = HtmlUtils.htmlEscape(pair[0]);
                if (!escaped.equals(pair[0])) {
                    pairs.add(new String[] {escaped, HtmlUtils.htmlEscape(pair[1])});
                }
            }
        }
        pairs.sort(Comparator.comparingInt((String[] p) -> p[0].length()).reversed());
        return pairs;
    }

    private String replacement(SensitiveValue v) {
        var byValue = known.computeIfAbsent(v.kind(), k -> new HashMap<>());
        var key = v.value().toLowerCase(Locale.ROOT);
        return byValue.computeIfAbsent(key, k -> {
            var n = byValue.size() + 1;
            var value = switch (v.kind()) {
                case NAME -> "Gast " + n;
                case STREET -> "Musterstraße " + n;
                case CITY -> "Musterstadt " + n;
                case EMAIL -> "gast" + n + "@" + EXAMPLE_DOMAIN;
                case POSTAL_CODE, PHONE, REFERENCE -> sameFormat(v.value());
            };
            produced.add(value);
            return value;
        });
    }

    private String email(String address, String domain, Set<String> keepDomains) {
        var lower = domain.toLowerCase(Locale.ROOT);
        if (keepDomains.contains(lower) || produced.contains(address)) {
            return address;
        }
        return patternEmails.computeIfAbsent(address.toLowerCase(Locale.ROOT), k -> {
            var value = "person" + (patternEmails.size() + 1) + "@" + EXAMPLE_DOMAIN;
            produced.add(value);
            return value;
        });
    }

    /** Same shape: each digit becomes a random digit, each letter a random letter; everything else stays. */
    private String sameFormat(String value) {
        if (produced.contains(value)) {
            return value;
        }
        return sameFormat.computeIfAbsent(value, k -> {
            var sb = new StringBuilder(k.length());
            for (var c : k.toCharArray()) {
                if (Character.isDigit(c)) {
                    sb.append((char) ('0' + random.nextInt(10)));
                } else if (Character.isUpperCase(c)) {
                    sb.append((char) ('A' + random.nextInt(26)));
                } else if (Character.isLowerCase(c)) {
                    sb.append((char) ('a' + random.nextInt(26)));
                } else {
                    sb.append(c);
                }
            }
            // Keep a leading 0 or + so that a phone number still looks like one.
            if (!k.isEmpty() && k.charAt(0) == '0') {
                sb.setCharAt(0, '0');
            }
            var result = sb.toString();
            produced.add(result);
            return result;
        });
    }

    private static String replaceWord(String text, String original, String replacement) {
        var pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(original) + "(?![\\p{L}\\p{N}])");
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String replaceAll(String text, Pattern pattern, java.util.function.Function<Matcher, String> f) {
        var matcher = pattern.matcher(text);
        var sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(f.apply(matcher)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static int digits(String s) {
        return (int) s.chars().filter(Character::isDigit).count();
    }
}
