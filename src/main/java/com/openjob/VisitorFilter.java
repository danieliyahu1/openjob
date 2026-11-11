package com.openjob;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Makes every browser a visitor without asking it to sign up.
 *
 * <p>An id is minted on the first page load and travels as a long-lived cookie.
 * Mutating paths refuse a request that has no cookie instead of creating an
 * identity for it, so a cookie-blocking browser or a script cannot mint a new
 * agent (and a new workspace) on every call.
 *
 * <p>New identities are limited per client address: someone who just landed
 * can start a few, but a loop cannot fill the workspace volume.
 */
@Component
class VisitorFilter implements Filter {

    /** The only paths that create an identity. */
    private static final Set<String> PAGES = Set.of("/", "/index.html", "/automations.html");

    /** The paths that need one; everything else (agent API, feedback) is open. */
    private static final Set<String> NEEDS_ID = Set.of("/message", "/api/automations");

    private static final String MISSING =
            "Your browser has no session cookie. Open the app in a browser that allows cookies "
                    + "and try again.";

    private final RateLimiter newIdentities = new RateLimiter(5, Duration.ofDays(1));
    private final boolean secureCookie;

    VisitorFilter(@Value("${openjob.cookie.secure:false}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse answer = (HttpServletResponse) response;
        String path = http.getServletPath();

        // Old bookmarks: there is no sign-in page any more.
        if ("/login.html".equals(path)) {
            answer.sendRedirect("/");
            return;
        }

        String id = VisitorIdentity.from(http);
        if (id != null) {
            http.setAttribute(VisitorIdentity.ATTRIBUTE, id);
            chain.doFilter(request, response);
            return;
        }

        if (!PAGES.contains(path)) {
            if (NEEDS_ID.contains(path) || path.startsWith("/api/automations/")) {
                answer.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                answer.setContentType(MediaType.TEXT_PLAIN_VALUE);
                answer.getWriter().write(MISSING);
            } else {
                chain.doFilter(request, response);
            }
            return;
        }

        long wait = newIdentities.permit(ClientAddress.of(http));
        if (wait > 0) {
            tooManySessions(answer, wait);
            return;
        }
        String minted = VisitorIdentity.mint();
        answer.addHeader(HttpHeaders.SET_COOKIE,
                VisitorIdentity.cookie(minted, secureCookie).toString());
        http.setAttribute(VisitorIdentity.ATTRIBUTE, minted);
        chain.doFilter(request, response);
    }

    /** A tester should almost never see this; a scanner sees it and stops. */
    private static void tooManySessions(HttpServletResponse response, long wait) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.getWriter().write(String.format("""
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>OpenJob</title></head>
                <body style="font:16px system-ui,sans-serif;max-width:34rem;margin:4rem auto;padding:0 1rem">
                <h1 style="font-size:1.25rem">Too many new sessions from this network</h1>
                <p style="color:#475569">OpenJob started several fresh sessions here today. Try again
                in about %d hours, or come back tomorrow.</p>
                </body></html>
                """, Math.max(1, wait / 3600)));
    }
}
