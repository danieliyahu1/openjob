package com.openjob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void prefersTheCloudflareHeader() {
        request.addHeader("CF-Connecting-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToTheFirstForwardedAddress() {
        request.addHeader("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1");

        assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoresBlankHeaders() {
        request.addHeader("CF-Connecting-IP", "  ");
        request.addHeader("X-Forwarded-For", "");
        request.setRemoteAddr("127.0.0.1");

        assertThat(ClientAddress.of(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void fallsBackToTheConnectionAddress() {
        request.setRemoteAddr("127.0.0.1");

        assertThat(ClientAddress.of(request)).isEqualTo("127.0.0.1");
    }
}
