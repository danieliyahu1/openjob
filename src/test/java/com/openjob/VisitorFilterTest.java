package com.openjob;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The visitor cookie is the whole account: minted once, required thereafter. */
class VisitorFilterTest {

    private final VisitorFilter filter = new VisitorFilter(false);

    @Test
    void theFirstPageLoadMintsAnIdentity() throws Exception {
        MockHttpServletRequest request = request("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String id = (String) request.getAttribute(VisitorIdentity.ATTRIBUTE);
        assertThat(id).matches("a-[0-9a-f]{24}");
        assertThat(chain.getRequest()).isNotNull();
        Cookie cookie = response.getCookie(VisitorIdentity.COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo((int) VisitorIdentity.LIFETIME.toSeconds());
        assertThat(cookie.getValue()).isEqualTo(id);
    }

    @Test
    void anExistingCookieIsKeptAndNotReissued() throws Exception {
        MockHttpServletRequest request = request("GET", "/");
        request.setCookies(new Cookie(VisitorIdentity.COOKIE, "a-abcdefabcdefabcdefabcdef"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(VisitorIdentity.ATTRIBUTE))
                .isEqualTo("a-abcdefabcdefabcdefabcdef");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void aMalformedCookieIsReplaced() throws Exception {
        MockHttpServletRequest request = request("GET", "/");
        request.setCookies(new Cookie(VisitorIdentity.COOKIE, "' OR 1=1 --"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(VisitorIdentity.ATTRIBUTE))
                .asString().matches("a-[0-9a-f]{24}");
        assertThat(response.getCookie(VisitorIdentity.COOKIE)).isNotNull();
    }

    @Test
    void aMessageWithoutACookieIsRejectedInsteadOfMintingAnAgent() throws Exception {
        MockHttpServletRequest request = request("POST", "/message");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getCookie(VisitorIdentity.COOKIE)).isNull();
        assertThat(chain.getRequest()).isNull();
        assertThat(request.getAttribute(VisitorIdentity.ATTRIBUTE)).isNull();
    }

    @Test
    void theOpenEndpointsStayOpen() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/feedback");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void newIdentitiesAreLimitedPerAddress() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(pageFrom("203.0.113.7"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(pageFrom("203.0.113.7"), limited, new MockFilterChain());

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getCookie(VisitorIdentity.COOKIE)).isNull();

        // Another network is unaffected.
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(pageFrom("198.51.100.9"), other, new MockFilterChain());
        assertThat(other.getStatus()).isEqualTo(200);
    }

    @Test
    void anOldLoginBookmarkGoesToTheApp() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/login.html"), response,
                new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static MockHttpServletRequest pageFrom(String address) {
        MockHttpServletRequest request = request("GET", "/");
        request.addHeader("CF-Connecting-IP", address);
        return request;
    }
}
