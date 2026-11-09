package com._early4coffee.tinylink.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validation rules for the link a user wants to shorten and for custom aliases.
 * Everything here is pure string and address parsing. It never makes a DNS lookup
 * or a network call.
 */
public final class UrlRules {

    public static final int MAX_URL_LENGTH = 2048;

    private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final Set<String> RESERVED_ALIASES =
            Set.of("api", "stats", "analytics", "shorten", "actuator", "health", "admin");
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final String[] BLOCKED_SUFFIXES = {
            ".localhost", ".local", ".internal", ".localdomain", ".home.arpa", ".lan", ".intranet"
    };

    private UrlRules() {
    }

    /** Returns the trimmed URL, or throws IllegalArgumentException with a user-facing message. */
    public static String validateTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            throw bad("Original URL is required");
        }
        String url = raw.trim();
        if (url.length() > MAX_URL_LENGTH) {
            throw bad("Original URL is too long (maximum " + MAX_URL_LENGTH + " characters)");
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == 0x7f) {
                throw bad("Original URL must not contain spaces or control characters");
            }
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw bad("Original URL is not a valid URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw bad("Original URL must start with http:// or https://");
        }
        if (uri.getRawUserInfo() != null) {
            throw bad("URLs with a username or password in them are not allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw bad("Original URL must contain a valid host name");
        }
        if (isBlockedHost(host)) {
            throw bad("Links to local or private addresses are not allowed");
        }
        return url;
    }

    /** Returns the trimmed alias, or null when the user did not ask for one. */
    public static String validateAlias(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String alias = raw.trim();
        if (!ALIAS.matcher(alias).matches()) {
            throw bad("Custom alias must be 3 to 32 characters: letters, digits, hyphen or underscore");
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw bad("That alias is reserved, please pick another");
        }
        return alias;
    }

    static boolean isBlockedHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        if (host.startsWith("[")) {
            return isBlockedIpv6(host);
        }

        Matcher m = IPV4.matcher(host);
        if (m.matches()) {
            int[] o = new int[4];
            for (int i = 0; i < 4; i++) {
                o[i] = Integer.parseInt(m.group(i + 1));
                if (o[i] > 255) {
                    return true;
                }
            }
            return isBlockedIpv4(o);
        }

        // Real top-level domains are never numeric, so things like http://2130706433
        // or http://127.1 (which resolve to 127.0.0.1) are rejected here.
        String lastLabel = host.substring(host.lastIndexOf('.') + 1);
        if (lastLabel.matches("\\d+") || lastLabel.startsWith("0x")) {
            return true;
        }

        // A public host name always has a dot. Single labels like "intranet" are internal.
        if (host.equals("localhost") || !host.contains(".")) {
            return true;
        }
        for (String suffix : BLOCKED_SUFFIXES) {
            if (host.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockedIpv4(int[] o) {
        int a = o[0];
        int b = o[1];
        return a == 0
                || a == 10
                || a == 127
                || (a == 100 && b >= 64 && b <= 127)
                || (a == 169 && b == 254)
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168)
                || (a == 192 && b == 0 && o[2] == 0)
                || a >= 224;
    }

    private static boolean isBlockedIpv6(String bracketed) {
        String literal = bracketed.substring(1, bracketed.length() - 1);
        try {
            // A literal address is parsed locally, there is no DNS lookup.
            InetAddress address = InetAddress.getByName(literal);
            byte[] bytes = address.getAddress();
            boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || uniqueLocal
                    || (bytes.length == 4 && isBlockedIpv4(new int[]{
                    bytes[0] & 0xff, bytes[1] & 0xff, bytes[2] & 0xff, bytes[3] & 0xff}));
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message);
    }
}
