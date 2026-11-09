package com._early4coffee.tinylink.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Works out the visitor's IP address for rate limiting.
 *
 * A visitor can send any X-Forwarded-For header they like, and proxies add the
 * address they saw to the END of the list. So the only trustworthy entry is the
 * one counted from the right, past the proxies you control.
 *
 * tinylink.client-ip.proxy-hops:
 *   0 = no proxy in front of the app, use the connection address (the safe default)
 *   1 = one trusted proxy appends the client address as the last entry
 *   2 = two trusted proxies, and so on
 */
@Component
public class ClientIpResolver {

    private static final Pattern PLAUSIBLE_IP = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final int proxyHops;

    public ClientIpResolver(@Value("${tinylink.client-ip.proxy-hops:0}") int proxyHops) {
        this.proxyHops = proxyHops;
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (proxyHops <= 0) {
            return remote;
        }

        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return remote;
        }

        String[] parts = header.split(",");
        int index = parts.length - proxyHops;
        if (index < 0) {
            // Fewer entries than trusted proxies, so the header did not come through our chain.
            return remote;
        }

        String candidate = parts[index].trim();
        return PLAUSIBLE_IP.matcher(candidate).matches() ? candidate : remote;
    }
}
