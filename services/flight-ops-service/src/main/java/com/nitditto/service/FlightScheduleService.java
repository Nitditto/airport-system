package com.nitditto.service;

import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.FlightScheduleResponse;
import java.util.List;

public interface FlightScheduleService {

  FlightScheduleResponse createFlightSchedule(Long airlineId,
      FlightScheduleRequest flightScheduleRequest) throws Exception;

  FlightScheduleResponse getFlightScheduleById(Long id) throws Exception;

  List<FlightScheduleResponse> getFlightScheduleByAirline(Long airlineId);

  FlightScheduleResponse updateFlightSchedule(Long id,
      FlightScheduleRequest flightScheduleRequest) throws Exception;

  void deleteFlightScheduleById(Long id) throws Exception;
}
