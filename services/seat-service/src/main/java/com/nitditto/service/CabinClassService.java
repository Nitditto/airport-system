package com.nitditto.service;

import com.nitditto.enums.CabinClassType;
import com.nitditto.payload.request.CabinClassRequest;
import com.nitditto.payload.response.CabinClassResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CabinClassService {

    CabinClassResponse createCabinClass(CabinClassRequest cabinClassRequest);

    CabinClassResponse getCabinClassById(UUID id);

    List<CabinClassResponse> getCabinClassesByAircraftId(UUID aircraftId);

    CabinClassResponse getByAircraftIdAndName(UUID aircraftId, CabinClassType name);

    /** Hạng ghế còn bán của mỗi máy bay theo loại; máy bay không có hạng đó thì vắng trong map. */
    Map<UUID, CabinClassResponse> getBookableByAircraftIds(List<UUID> aircraftIds, CabinClassType name);

    CabinClassResponse updateCabinClass(UUID id, CabinClassRequest cabinClassRequest);

    void deleteCabinClass(UUID id);
}
