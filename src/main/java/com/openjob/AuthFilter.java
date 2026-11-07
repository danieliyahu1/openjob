package com.openjob;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Session gate for the browser surface only. The token API the agent calls and
 * the anonymous feedback endpoint keep their own auth and stay open here.
 */
@Component
class AuthFilter implements Filter {

    private static final Set<String> GUARDED = Set.of("/", "/index.html", "/automations.html", "/message");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String path = http.getServletPath();
        boolean api = path.equals("/api/automations") || path.startsWith("/api/automations/");

        if ("/login.html".equals(path)) {
            if (signedIn(http)) {
                ((HttpServletResponse) response).sendRedirect("/");
            } else {
                chain.doFilter(request, response);
            }
            return;
        }
        if ((!GUARDED.contains(path) && !api) || signedIn(http)) {
            chain.doFilter(request, response);
            return;
        }
        if (api || "/message".equals(path)) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            ((HttpServletResponse) response).sendRedirect("/login.html");
        }
    }

    private static boolean signedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(AuthController.USER) != null;
    }
}
