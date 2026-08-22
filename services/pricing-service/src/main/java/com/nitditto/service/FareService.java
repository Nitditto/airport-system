package com.nitditto.service;

import com.nitditto.model.Fare;
import com.nitditto.payload.request.FareRequest;
import com.nitditto.payload.response.FareResponse;

import java.util.List;
import java.util.Map;

public interface FareService {

    FareResponse createFare(FareRequest request) throws Exception;

    FareResponse getFareById(Long id) throws Exception;

    List<FareResponse> getFaresByFlightIdAndCabinClassId(Long flightId, Long cabinClassId);

    FareResponse updateFare(Long id, FareRequest
            request) throws Exception;

    void deleteFareById(Long id) throws Exception;

    List<Fare> getFares();

    Map<Long, FareResponse> getLowestFarePerFlight(List<Long> flightIds, Long cabinClassId);

    Map<Long, FareResponse> getFareByIds(List<Long> ids);
}
