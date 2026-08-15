package com.nitditto.service;

import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;
import java.util.List;

public interface AirportService {

  AirportResponse createAirport(AirportRequest airportRequest) throws Exception;

  AirportResponse getAirportById(Long id) throws Exception;

  List<AirportResponse> getAllAirports();

  AirportResponse updateAirport(Long id, AirportRequest airportRequest) throws Exception;

  void deleteAirportById(Long id) throws Exception;

  List<AirportResponse> getAirportsByCityId(Long cityId);
}
