package com.openjob;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The visitor's address, used as the rate-limit key.
 *
 * <p>Cloudflare overwrites {@code CF-Connecting-IP} with the real client, so it
 * is the authoritative value and cannot be spoofed through the tunnel. The
 * {@code X-Forwarded-For} fallback covers a proxy that only sets that header;
 * it takes the first entry, so it assumes the proxy prepends.
 */
final class ClientAddress {

    private ClientAddress() {
    }

    static String of(HttpServletRequest request) {
        String connecting = request.getHeader("CF-Connecting-IP");
        if (connecting != null && !connecting.isBlank()) {
            return connecting.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
