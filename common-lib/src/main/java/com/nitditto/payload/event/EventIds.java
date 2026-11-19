package com.nitditto.payload.event;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Sinh {@code eventId} UUIDv7 (RFC 9562): 48 bit timestamp ms + ngẫu nhiên, nên id tăng dần theo thời gian publish. */
public final class EventIds {

  private static final SecureRandom RANDOM = new SecureRandom();

  private EventIds() {
  }

  public static UUID newEventId() {
    return newEventId(Instant.now());
  }

  static UUID newEventId(final Instant at) {
    final long millis = at.toEpochMilli() & 0xFFFF_FFFF_FFFFL;
    final long randA = RANDOM.nextLong() & 0x0FFFL;
    final long msb = (millis << 16) | 0x7000L | randA;
    final long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
    return new UUID(msb, lsb);
  }
}
