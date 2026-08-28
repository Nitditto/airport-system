package com.nitditto.service;

import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.FlightScheduleResponse;
import java.util.List;
import java.util.UUID;

public interface FlightScheduleService {

  FlightScheduleResponse createFlightSchedule(UUID airlineId,
      FlightScheduleRequest flightScheduleRequest);

  FlightScheduleResponse getFlightScheduleById(UUID id);

  List<FlightScheduleResponse> getFlightScheduleByAirline(UUID airlineId);

  FlightScheduleResponse updateFlightSchedule(UUID id,
      FlightScheduleRequest flightScheduleRequest);

  void deleteFlightScheduleById(UUID id);
}
