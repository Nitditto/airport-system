package com.nitditto.service;

import com.nitditto.enums.AirlineStatus;
import com.nitditto.payload.request.AirlineRequest;
import com.nitditto.payload.response.AirlineDropDownItem;
import com.nitditto.payload.response.AirlineResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public interface AirlineService {

  AirlineResponse createAirline(AirlineRequest airlineRequest, Long ownerId);

  AirlineResponse getAirlineByOwner(Long ownerId) throws Exception;

  AirlineResponse getAirlineById(Long airlineId) throws Exception;

  Page<AirlineResponse> getAllAirlines(Pageable pageable);

  AirlineResponse updateAirline(AirlineRequest airlineRequest, Long ownerId) throws Exception;

  void deleteAirline(Long id, Long ownerId) throws Exception;

  AirlineResponse changeStatusByAdmin(Long airlineId, AirlineStatus status) throws Exception;

  List<AirlineDropDownItem> getAirlineDropDown();

}
