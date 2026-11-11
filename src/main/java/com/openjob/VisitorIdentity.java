package com.openjob;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The anonymous account id. There is no signup: the first page load mints a
 * random id and the browser sends it back as a cookie, and that cookie is the
 * account.
 *
 * <p>The id is 96 random bits, so it is a bearer credential. It is never placed
 * in a URL or a log line; knowing it means being that visitor. Clearing site
 * data or switching browsers starts a new account, which is the trade this
 * product makes on purpose.
 */
final class VisitorIdentity {

    /** Request attribute the filter writes and the controllers read. */
    static final String ATTRIBUTE = "openjob.visitor";

    static final String COOKIE = "openjob_id";

    /** Long enough that a tester can come back the next month. */
    static final Duration LIFETIME = Duration.ofDays(180);

    private static final Pattern FORMAT = Pattern.compile("a-[0-9a-f]{24}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private VisitorIdentity() {
    }

    /** A new account id. The prefix makes it obvious in the database. */
    static String mint() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return "a-" + HexFormat.of().formatHex(bytes);
    }

    /** The id in the request's cookie, or null when absent or malformed. */
    static String from(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                return valid(value) ? value : null;
            }
        }
        return null;
    }

    static boolean valid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    /** The visitor id of the current request. */
    static String require(HttpServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session cookie");
        }
        return id.toString();
    }

    static ResponseCookie cookie(String id, boolean secure) {
        return ResponseCookie.from(COOKIE, id)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(LIFETIME)
                .build();
    }
}
