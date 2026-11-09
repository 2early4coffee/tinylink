package com._early4coffee.tinylink.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remote, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    @Test
    void withNoTrustedProxyTheHeaderIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4"))).isEqualTo("10.0.0.5");
    }

    @Test
    void oneTrustedProxyUsesTheLastEntryNotTheForgedFirstOne() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        assertThat(resolver.resolve(request("10.0.0.5", "6.6.6.6, 203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void twoTrustedProxiesSkipTheLastEntry() {
        ClientIpResolver resolver = new ClientIpResolver(2);
        assertThat(resolver.resolve(request("10.0.0.5", "6.6.6.6, 203.0.113.9, 35.1.1.1"))).isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToTheConnectionAddressWhenTheHeaderIsMissingShortOrJunk() {
        ClientIpResolver resolver = new ClientIpResolver(2);
        assertThat(resolver.resolve(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9"))).isEqualTo("10.0.0.5");
        assertThat(new ClientIpResolver(1).resolve(request("10.0.0.5", "<script>alert(1)</script>"))).isEqualTo("10.0.0.5");
    }
}
