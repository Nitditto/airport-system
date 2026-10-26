package com.nitditto.service;

import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.FlightInstanceResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightInstanceService {

  FlightInstanceResponse createFlightInstance(UUID userId,
      FlightInstanceRequest flightInstanceRequest);

  void createFlightInstances(UUID airlineId, List<FlightInstanceRequest> flightInstanceRequests);

  FlightInstanceResponse getFlightInstanceById(UUID id);

  Page<FlightInstanceResponse> getByAirlineId(UUID userId,
      UUID departureAirportId,
      UUID arrivalAirportId,
      UUID flightId,
      LocalDate onDate,
      Pageable pageable);

  /** Tìm chuyến bay công khai cho khách, không phụ thuộc người dùng đăng nhập. */
  Page<FlightInstanceResponse> searchPublic(UUID departureAirportId, UUID arrivalAirportId,
      LocalDate onDate, int passengers, Pageable pageable);

  /** Batch cho booking-service: số lời gọi sang service khác cố định theo số loại dữ liệu, không theo số chuyến. */
  Map<UUID, FlightInstanceResponse> getByIds(List<UUID> ids);

  FlightInstanceResponse updateFlightInstance(UUID id, FlightInstanceRequest flightInstanceRequest);

  void deleteFlightInstance(UUID id);
}
