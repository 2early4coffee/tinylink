package com._early4coffee.tinylink.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlRulesTest {

    @Test
    void acceptsNormalPublicUrls() {
        assertThat(UrlRules.validateTarget("https://example.com/a/b?x=1#top")).isEqualTo("https://example.com/a/b?x=1#top");
        assertThat(UrlRules.validateTarget("  http://sub.example.co.ke  ")).isEqualTo("http://sub.example.co.ke");
        assertThat(UrlRules.validateTarget("https://8.8.8.8/dns")).isEqualTo("https://8.8.8.8/dns");
    }

    @Test
    void rejectsOtherSchemesAndMissingHost() {
        for (String bad : new String[]{"ftp://example.com", "javascript:alert(1)", "data:text/html,hi",
                "example.com", "http://", "https:///path", "//example.com"}) {
            assertThatThrownBy(() -> UrlRules.validateTarget(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsLocalAndPrivateTargets() {
        for (String bad : new String[]{
                "http://localhost:8080/x", "http://LOCALHOST/", "http://127.0.0.1/", "http://127.1/",
                "http://2130706433/", "http://0x7f000001/", "http://10.1.2.3/", "http://172.16.0.1/",
                "http://172.31.255.255/", "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data",
                "http://100.64.0.1/", "http://0.0.0.0/", "http://[::1]/", "http://[fd00::1]/",
                "http://[fe80::1]/", "http://[::ffff:127.0.0.1]/", "http://intranet/", "http://printer.local/",
                "http://metadata.google.internal/", "http://app.localhost/", "http://300.1.1.1/"}) {
            assertThatThrownBy(() -> UrlRules.validateTarget(bad))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsCredentialsSpacesControlCharsAndHugeUrls() {
        assertThatThrownBy(() -> UrlRules.validateTarget("http://google.com@evil.example/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlRules.validateTarget("http://example.com/a b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlRules.validateTarget("http://example.com/a\r\nSet-Cookie: x=1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlRules.validateTarget("https://example.com/" + "a".repeat(2100))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlRules.validateTarget("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlRules.validateTarget(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicAddressesNextToPrivateRangesAreAllowed() {
        assertThat(UrlRules.isBlockedHost("172.32.0.1")).isFalse();
        assertThat(UrlRules.isBlockedHost("172.15.0.1")).isFalse();
        assertThat(UrlRules.isBlockedHost("100.63.0.1")).isFalse();
        assertThat(UrlRules.isBlockedHost("100.128.0.1")).isFalse();
        assertThat(UrlRules.isBlockedHost("example.com.")).isFalse();
    }

    @Test
    void aliasRules() {
        assertThat(UrlRules.validateAlias(null)).isNull();
        assertThat(UrlRules.validateAlias("   ")).isNull();
        assertThat(UrlRules.validateAlias(" my-link_1 ")).isEqualTo("my-link_1");
        for (String bad : new String[]{"ab", "a".repeat(33), "has space", "slash/inside", "dots..", "emoji☃x", "Stats", "API", "actuator"}) {
            assertThatThrownBy(() -> UrlRules.validateAlias(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
