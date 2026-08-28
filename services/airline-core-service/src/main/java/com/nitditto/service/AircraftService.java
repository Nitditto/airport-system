package com.nitditto.service;

import com.nitditto.payload.request.AircraftRequest;
import com.nitditto.payload.response.AircraftResponse;
import java.util.List;
import java.util.UUID;

public interface AircraftService {

  AircraftResponse createAircraft(AircraftRequest aircraftRequest, UUID ownerId);

  AircraftResponse getAircraftById(UUID id);

  List<AircraftResponse> listAllAircraftByOwner(UUID ownerId);

  AircraftResponse updateAircraft(UUID id, AircraftRequest aircraftRequest, UUID ownerId);

  void deleteAircraft(UUID id, UUID ownerId);

}
