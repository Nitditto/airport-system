package com.nitditto.service.integration;

import com.nitditto.mapper.FlightInstanceMapper;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightInstance;
import com.nitditto.payload.response.FlightInstanceResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dựng {@link FlightInstanceResponse} cho một danh sách chuyến: nạp hãng/máy bay/sân bay bằng batch
 * ({@link ReferenceDataLoader}) rồi ghép trong bộ nhớ. Dùng chung cho CRUD và tìm kiếm chuyến bay.
 */
@Component
@RequiredArgsConstructor
public class FlightInstanceAssembler {

  private final ReferenceDataLoader referenceDataLoader;

  /** Map theo id chuyến, giữ thứ tự đầu vào. {@code flightById} thiếu entry thì chuyến đó không có thông tin Flight. */
  public Map<UUID, FlightInstanceResponse> assemble(final List<FlightInstance> instances,
      final Map<UUID, Flight> flightById) {
    if (instances.isEmpty()) {
      return Map.of();
    }
    final List<UUID> airlineIds = new ArrayList<>(instances.size());
    final List<UUID> aircraftIds = new ArrayList<>(instances.size());
    final List<UUID> airportIds = new ArrayList<>(instances.size() * 2);
    for (final FlightInstance instance : instances) {
      final Flight flight = flightById.get(instance.getFlightId());
      airlineIds.add(instance.getAirlineId());
      aircraftIds.add(flight != null ? flight.getAircraftId() : null);
      airportIds.add(departureAirportId(instance, flight));
      airportIds.add(arrivalAirportId(instance, flight));
    }
    final ReferenceData referenceData = this.referenceDataLoader.load(airlineIds, aircraftIds, airportIds);

    final Map<UUID, FlightInstanceResponse> responses = new LinkedHashMap<>(instances.size());
    for (final FlightInstance instance : instances) {
      final Flight flight = flightById.get(instance.getFlightId());
      responses.put(instance.getId(), FlightInstanceMapper.toResponse(instance, flight,
          referenceData.aircraft(flight != null ? flight.getAircraftId() : null),
          referenceData.airline(instance.getAirlineId()),
          referenceData.airport(departureAirportId(instance, flight)),
          referenceData.airport(arrivalAirportId(instance, flight))));
    }
    return responses;
  }

  /** Chuyến cũ có thể chưa lưu sân bay riêng — khi đó lấy theo tuyến bay gốc. */
  private static UUID departureAirportId(final FlightInstance instance, final Flight flight) {
    if (instance.getDepartureAirportId() != null) {
      return instance.getDepartureAirportId();
    }
    return flight != null ? flight.getDepartureAirportId() : null;
  }

  private static UUID arrivalAirportId(final FlightInstance instance, final Flight flight) {
    if (instance.getArrivalAirportId() != null) {
      return instance.getArrivalAirportId();
    }
    return flight != null ? flight.getArrivalAirportId() : null;
  }
}
