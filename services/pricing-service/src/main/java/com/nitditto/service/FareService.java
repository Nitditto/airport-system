package com.nitditto.service;

import com.nitditto.payload.request.FareRequest;
import com.nitditto.payload.response.FareResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FareService {

    FareResponse createFare(FareRequest request);

    FareResponse getFareById(UUID id);

    List<FareResponse> getFaresByFlightIdAndCabinClassId(UUID flightId, UUID cabinClassId);

    FareResponse updateFare(UUID id, FareRequest request);

    void deleteFareById(UUID id);

    List<FareResponse> getFares();

    Map<UUID, FareResponse> getLowestFarePerFlight(List<UUID> flightIds, UUID cabinClassId);

    Map<UUID, FareResponse> getFareByIds(List<UUID> ids);
}
