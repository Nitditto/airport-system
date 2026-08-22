package com.nitditto.service.impl;

import com.nitditto.mapper.FareMapper;
import com.nitditto.model.Fare;
import com.nitditto.payload.request.FareRequest;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.repository.FareRepository;
import com.nitditto.service.FareService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FareServiceImpl implements FareService {

    private final FareRepository fareRepository;

    @Override
    public FareResponse createFare(FareRequest request) throws Exception {

        if (fareRepository.existsByFlightIdAndCabinClassIdAndName(request.getFlightId(), request.getCabinClassId(),
                request.getName())) {
            throw new Exception("Fare already exist with provided name");
        }
        Fare fare = FareMapper.toEntity(request);
        Fare savedFare = fareRepository.save(fare);
        return FareMapper.toResponse(savedFare);
    }

    @Override
    public FareResponse getFareById(Long id) throws Exception {

        Fare fare = fareRepository.findById(id).orElseThrow(
                () -> new Exception("Fare not found with given Id"));
        return FareMapper.toResponse(fare);
    }

    @Override
    public List<FareResponse> getFaresByFlightIdAndCabinClassId(Long flightId, Long cabinClassId) {
        return fareRepository.findByFlightIdAndCabinClassId(
                flightId, cabinClassId).stream().map(FareMapper::toResponse).toList();
    }

    @Override
    public FareResponse updateFare(Long id, FareRequest request) throws Exception {
        Fare fare = fareRepository.findById(id).orElseThrow(
                () -> new Exception("Fare not found with given Id"));

        Long flightId = request.getFlightId() != null ? request.getFlightId() : fare.getFlightId();
        Long cabinClassId = request.getCabinClassId() != null ? request.getCabinClassId() : fare.getCabinClassId();
        String name = request.getName() != null ? request.getName() : fare.getName();

        if (fareRepository.existsByFlightIdAndCabinClassIdAndNameAndIdNot(flightId, cabinClassId, name, fare.getId())) {
            throw new Exception("Fare already exists with the same flight, cabin class, and name");
        }

        FareMapper.updateEntity(request, fare);
        Fare updatedFare = fareRepository.save(fare);
        return FareMapper.toResponse(updatedFare);
    }

    @Override
    public void deleteFareById(Long id) throws Exception {
        Fare fare = fareRepository.findById(id).orElseThrow(
                () -> new Exception("Fare not found with given Id")
        );
        fareRepository.delete(fare);
    }

    @Override
    public List<Fare> getFares() {
        return fareRepository.findAll();
    }

    @Override
    public Map<Long, FareResponse> getLowestFarePerFlight(List<Long> flightIds, Long cabinClassId) {
        if (flightIds == null || flightIds.isEmpty()) {
            return Map.of();
        }

        List<Fare> fares = fareRepository.findByFlightIdInAndCabinClassId(flightIds, cabinClassId);

        Map<Long, FareResponse> result = fares.stream()
                .collect(Collectors.toMap(
                        Fare::getFlightId, fare -> fare,
                        (existing, candidate) -> candidate.getTotalPrice() < existing.getTotalPrice() ? candidate : existing))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> FareMapper.toResponse(e.getValue())));

        return result;
    }

    @Override
    public Map<Long, FareResponse> getFareByIds(List<Long> ids) {
        List<Fare> fares = fareRepository.findAllById(ids);

        // [fare response, fare response]
        // { 1=> fare response, 2 => fare response}

        return fares.stream().collect(Collectors.toMap(Fare::getId, FareMapper::toResponse));
    }
}
