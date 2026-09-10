package com.nitditto.service;

import java.util.List;
import java.util.UUID;

import com.nitditto.payload.request.AncillaryRequest;
import com.nitditto.payload.response.AncillaryResponse;

public interface AncillaryService {
  AncillaryResponse createAncillary(UUID airlineId, AncillaryRequest request);

  AncillaryResponse getById(UUID id);

  List<AncillaryResponse> getByAirlineId(UUID airlineId);

  AncillaryResponse updateAncillary(UUID id, AncillaryRequest request);

  void deleteAncillary(UUID id);
}
