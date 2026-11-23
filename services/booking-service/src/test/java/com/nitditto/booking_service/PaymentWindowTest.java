package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.model.Booking;
import com.nitditto.service.PaymentWindow;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentWindowTest {

  @Test
  void expiryIsTheCreationTimePlusTheConfiguredWindow() {
    final Booking booking = new Booking();
    booking.setCreatedAt(Instant.parse("2026-11-20T03:00:00Z"));

    final PaymentWindow window = new PaymentWindow(20);

    assertThat(window.duration()).isEqualTo(Duration.ofMinutes(20));
    assertThat(window.expiresAt(booking)).isEqualTo(Instant.parse("2026-11-20T03:20:00Z"));
  }

  @Test
  void aBookingWithoutACreationTime_hasNoExpiry() {
    assertThat(new PaymentWindow(15).expiresAt(new Booking())).isNull();
  }

  @Test
  void hasExpired_isFalseInsideTheWindow_andTrueFromTheDeadlineOn() {
    final Booking booking = new Booking();
    booking.setCreatedAt(Instant.parse("2026-11-20T03:00:00Z"));
    final Instant deadline = Instant.parse("2026-11-20T03:15:00Z");

    assertThat(windowAt(deadline.minusSeconds(1)).hasExpired(booking)).isFalse();
    assertThat(windowAt(deadline).hasExpired(booking)).isTrue();
  }

  @Test
  void hasExpired_defersToTheLockedCheck_whenCreationTimeIsMissing() {
    assertThat(new PaymentWindow(15).hasExpired(new Booking())).isTrue();
  }

  private static PaymentWindow windowAt(final Instant now) {
    return new PaymentWindow(15, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
  }
}
