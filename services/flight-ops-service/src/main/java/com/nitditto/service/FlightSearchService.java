package com.nitditto.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nitditto.payload.request.FlightSearchRequest;
import com.nitditto.payload.response.FlightInstanceResponse;

public interface FlightSearchService {

  Page<FlightInstanceResponse> searchFlights(FlightSearchRequest request, Pageable pageable);
}
