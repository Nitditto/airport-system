package com.nitditto.payload.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventIdsTest {

  @Test
  void generatesVersion7RfcVariantIds() {
    final UUID id = EventIds.newEventId();

    assertEquals(7, id.version());
    assertEquals(2, id.variant());
  }

  @Test
  void idsAreUnique_andOrderedByTimestamp() {
    final UUID earlier = EventIds.newEventId(Instant.parse("2026-11-05T10:00:00Z"));
    final UUID later = EventIds.newEventId(Instant.parse("2026-11-05T10:00:01Z"));

    assertNotEquals(EventIds.newEventId(), EventIds.newEventId());
    assertTrue(Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits()) < 0);
  }
}
