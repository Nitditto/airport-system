package com.nitditto.service;

import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;
import java.util.List;
import java.util.UUID;

public interface AirportService {

  AirportResponse createAirport(AirportRequest airportRequest);

  AirportResponse getAirportById(UUID id);

  List<AirportResponse> getAllAirports();

  AirportResponse updateAirport(UUID id, AirportRequest airportRequest);

  void deleteAirportById(UUID id);

  List<AirportResponse> getAirportsByCityId(UUID cityId);
}
