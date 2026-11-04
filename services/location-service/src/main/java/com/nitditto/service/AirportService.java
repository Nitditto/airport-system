package com.nitditto.service;

import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AirportService {

  AirportResponse createAirport(AirportRequest airportRequest);

  AirportResponse getAirportById(UUID id);

  /** Tra nhiều sân bay trong một lần cho service khác; id không tồn tại bị bỏ qua (không có key trong map). */
  Map<UUID, AirportResponse> getAirportsByIds(List<UUID> ids);

  List<AirportResponse> getAllAirports();

  AirportResponse updateAirport(UUID id, AirportRequest airportRequest);

  void deleteAirportById(UUID id);

  List<AirportResponse> getAirportsByCityId(UUID cityId);
}
