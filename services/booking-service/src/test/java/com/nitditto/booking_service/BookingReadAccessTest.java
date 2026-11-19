package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nitditto.exception.ForbiddenException;
import com.nitditto.model.Booking;
import com.nitditto.service.BookingReadAccess;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BookingReadAccessTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID airlineId = UUID.randomUUID();
  private final AtomicInteger airlineLookups = new AtomicInteger();

  @Test
  void bookingOwner_canRead_withoutAnyAirlineLookup() {
    final Booking booking = booking(this.ownerId, this.airlineId);

    assertThatCode(() -> BookingReadAccess.require(booking, this.ownerId, false, () -> this.lookup(null)))
        .doesNotThrowAnyException();

    assertThatCodeLookups(0);
  }

  @Test
  void ownerOfTheBookingsAirline_canRead() {
    final Booking booking = booking(this.ownerId, this.airlineId);
    final UUID airlineOwner = UUID.randomUUID();

    assertThatCode(() -> BookingReadAccess.require(booking, airlineOwner, false, () -> this.lookup(this.airlineId)))
        .doesNotThrowAnyException();
  }

  @Test
  void anotherCustomer_whoOwnsNoAirline_isForbidden() {
    final Booking booking = booking(this.ownerId, this.airlineId);

    assertThatThrownBy(() -> BookingReadAccess.require(booking, UUID.randomUUID(), false, () -> this.lookup(null)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void ownerOfADifferentAirline_isForbidden() {
    final Booking booking = booking(this.ownerId, this.airlineId);

    assertThatThrownBy(() -> BookingReadAccess.require(booking, UUID.randomUUID(), false,
        () -> this.lookup(UUID.randomUUID()))).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void bookingWithoutAirline_isNeverReadableByAnonymousAirlineMatch() {
    // null == null không được coi là khớp hãng: user không có hãng + booking chưa có hãng vẫn bị từ chối.
    final Booking booking = booking(this.ownerId, null);

    assertThatThrownBy(() -> BookingReadAccess.require(booking, UUID.randomUUID(), false, () -> this.lookup(null)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void admin_canRead_anyBooking_withoutAnyAirlineLookup() {
    final Booking booking = booking(this.ownerId, this.airlineId);

    assertThatCode(() -> BookingReadAccess.require(booking, UUID.randomUUID(), true, () -> this.lookup(null)))
        .doesNotThrowAnyException();

    assertThatCodeLookups(0);
  }

  @Test
  void hasAdminRole_readsTheGatewayRolesHeader_exactly() {
    org.junit.jupiter.api.Assertions.assertTrue(BookingReadAccess.hasAdminRole("ROLE_SYSTEM_ADMIN"));
    org.junit.jupiter.api.Assertions.assertTrue(BookingReadAccess.hasAdminRole("ROLE_USER, ROLE_SYSTEM_ADMIN"));
    org.junit.jupiter.api.Assertions.assertFalse(BookingReadAccess.hasAdminRole("ROLE_USER"));
    org.junit.jupiter.api.Assertions.assertFalse(BookingReadAccess.hasAdminRole("ROLE_AIRLINE_OWNER"));
    // không khớp theo chuỗi con: tên role giống một phần không được tính là admin
    org.junit.jupiter.api.Assertions.assertFalse(BookingReadAccess.hasAdminRole("ROLE_SYSTEM_ADMINISTRATOR"));
    org.junit.jupiter.api.Assertions.assertFalse(BookingReadAccess.hasAdminRole("not_ROLE_SYSTEM_ADMIN"));
    org.junit.jupiter.api.Assertions.assertFalse(BookingReadAccess.hasAdminRole(""));
    org.junit.jupiter.api.Assertions.assertFalse(BookingReadAccess.hasAdminRole(null));
  }

  private UUID lookup(final UUID result) {
    this.airlineLookups.incrementAndGet();
    return result;
  }

  private void assertThatCodeLookups(final int expected) {
    if (this.airlineLookups.get() != expected) {
      throw new AssertionError("airline lookups: expected " + expected + " but was " + this.airlineLookups.get());
    }
  }

  private static Booking booking(final UUID userId, final UUID airlineId) {
    final Booking booking = new Booking();
    booking.setUserId(userId);
    booking.setAirlineId(airlineId);
    return booking;
  }
}
