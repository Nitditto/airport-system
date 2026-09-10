package com.nitditto.service;

import java.util.List;
import java.util.UUID;

import com.nitditto.enums.AncillaryType;
import com.nitditto.payload.request.FlightCabinAncillaryRequest;
import com.nitditto.payload.response.FlightCabinAncillaryResponse;

public interface FlightCabinAncillaryService {

    FlightCabinAncillaryResponse createFlightCabinAncillary(FlightCabinAncillaryRequest request);

    FlightCabinAncillaryResponse getById(UUID id);

    List<FlightCabinAncillaryResponse> getByFlightAndCabinClass(UUID flightId, UUID cabinClassId);

    List<FlightCabinAncillaryResponse> getAllByIds(List<UUID> ids);

    FlightCabinAncillaryResponse getByFlightIdAndCabinClassIdAndType(UUID flightId, UUID cabinClassId, AncillaryType ancillaryType);

    List<FlightCabinAncillaryResponse> getAllByFlightIdAndCabinClassIdAndType(UUID flightId, UUID cabinClassId, AncillaryType ancillaryType);

    FlightCabinAncillaryResponse update(UUID id, FlightCabinAncillaryRequest request);

    void deleteById(UUID id);

    Double calculateAncillaryPrice(List<UUID> ancillaryIds);
}
