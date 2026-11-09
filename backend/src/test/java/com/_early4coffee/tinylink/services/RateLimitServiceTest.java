package com._early4coffee.tinylink.services;

import com._early4coffee.tinylink.services.RateLimitService.Bucket;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    /** A clock the test can move forward. */
    static class TestClock extends Clock {
        long millis = 1_000_000L;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    @Test
    void blocksAfterTheMinuteLimitAndReopensAfterAMinute() {
        TestClock clock = new TestClock();
        RateLimitService service = new RateLimitService(clock, 2, 10, 120);

        assertThat(service.check(Bucket.SHORTEN, "1.1.1.1").allowed()).isTrue();
        assertThat(service.check(Bucket.SHORTEN, "1.1.1.1").allowed()).isTrue();

        RateLimitService.Result blocked = service.check(Bucket.SHORTEN, "1.1.1.1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);

        clock.millis += 61_000;
        assertThat(service.check(Bucket.SHORTEN, "1.1.1.1").allowed()).isTrue();
    }

    @Test
    void enforcesTheHourLimitAcrossMinutes() {
        TestClock clock = new TestClock();
        RateLimitService service = new RateLimitService(clock, 5, 6, 120);

        for (int i = 0; i < 6; i++) {
            assertThat(service.check(Bucket.SHORTEN, "2.2.2.2").allowed()).as("request " + i).isTrue();
            clock.millis += 61_000;
        }
        assertThat(service.check(Bucket.SHORTEN, "2.2.2.2").allowed()).isFalse();

        clock.millis += 3_600_000;
        assertThat(service.check(Bucket.SHORTEN, "2.2.2.2").allowed()).isTrue();
    }

    @Test
    void clientsAndBucketsAreIndependent() {
        RateLimitService service = new RateLimitService(new TestClock(), 1, 10, 3);

        assertThat(service.check(Bucket.SHORTEN, "3.3.3.3").allowed()).isTrue();
        assertThat(service.check(Bucket.SHORTEN, "3.3.3.3").allowed()).isFalse();
        assertThat(service.check(Bucket.SHORTEN, "4.4.4.4").allowed()).isTrue();

        for (int i = 0; i < 3; i++) {
            assertThat(service.check(Bucket.REDIRECT, "3.3.3.3").allowed()).isTrue();
        }
        assertThat(service.check(Bucket.REDIRECT, "3.3.3.3").allowed()).isFalse();
    }

    @Test
    void remainingCountsDown() {
        RateLimitService service = new RateLimitService(new TestClock(), 3, 10, 120);
        assertThat(service.check(Bucket.SHORTEN, "5.5.5.5").remaining()).isEqualTo(2);
        assertThat(service.check(Bucket.SHORTEN, "5.5.5.5").remaining()).isEqualTo(1);
        assertThat(service.check(Bucket.SHORTEN, "5.5.5.5").remaining()).isEqualTo(0);
    }

    @Test
    void tooManyDistinctClientsDoesNotGrowWithoutLimit() {
        RateLimitService service = new RateLimitService(new TestClock(), 5, 50, 120);
        for (int i = 0; i < 60_000; i++) {
            service.check(Bucket.SHORTEN, "ip-" + i);
        }
        assertThat(service.trackedClients()).isLessThanOrEqualTo(50_000);
    }
}
