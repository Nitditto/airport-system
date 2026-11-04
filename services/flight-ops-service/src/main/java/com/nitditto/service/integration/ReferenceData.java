package com.nitditto.service.integration;

import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import java.util.Map;
import java.util.UUID;

/**
 * Dữ liệu tham chiếu (hãng, máy bay, sân bay) của các service khác, đã được nạp sẵn bằng batch.
 * Mapper tra cứu trong bộ nhớ qua các hàm dưới đây — id null hoặc không tồn tại trả về null.
 */
public record ReferenceData(
    Map<UUID, AirlineResponse> airlines,
    Map<UUID, AircraftResponse> aircraft,
    Map<UUID, AirportResponse> airports) {

  private static final ReferenceData EMPTY = new ReferenceData(Map.of(), Map.of(), Map.of());

  public ReferenceData {
    airlines = Map.copyOf(airlines);
    aircraft = Map.copyOf(aircraft);
    airports = Map.copyOf(airports);
  }

  public static ReferenceData empty() {
    return EMPTY;
  }

  public AirlineResponse airline(final UUID id) {
    return id != null ? this.airlines.get(id) : null;
  }

  public AircraftResponse aircraft(final UUID id) {
    return id != null ? this.aircraft.get(id) : null;
  }

  public AirportResponse airport(final UUID id) {
    return id != null ? this.airports.get(id) : null;
  }
}
