package com.nitditto.service;

import com.nitditto.enums.AirlineStatus;
import com.nitditto.payload.request.AirlineRequest;
import com.nitditto.payload.response.AirlineDropDownItem;
import com.nitditto.payload.response.AirlineResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AirlineService {

  AirlineResponse createAirline(AirlineRequest airlineRequest, UUID ownerId);

  AirlineResponse getAirlineByOwner(UUID ownerId);

  AirlineResponse getAirlineById(UUID airlineId);

  Page<AirlineResponse> getAllAirlines(Pageable pageable);

  AirlineResponse updateAirline(AirlineRequest airlineRequest, UUID ownerId);

  void deleteAirline(UUID id, UUID ownerId);

  AirlineResponse changeStatusByAdmin(UUID airlineId, AirlineStatus status);

  List<AirlineDropDownItem> getAirlineDropDown();

}
