package com.nitditto.service.impl;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FlightInstanceMapper;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightInstance;
import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.service.FlightInstanceService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightInstanceServiceImpl implements FlightInstanceService {

    private final FlightRepository flightRepository;
    private final FlightInstanceRepository flightInstanceRepository;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;

    @Override
    @Transactional
    public FlightInstanceResponse createFlightInstance(final UUID userId,
            final FlightInstanceRequest flightInstanceRequest) {
        final AirlineResponse airlineResponse = this.airlineClient.getAirlineByOwner(userId).getData();
        final Flight flight = this.flightRepository.findById(flightInstanceRequest.getFlightId())
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id",
                        flightInstanceRequest.getFlightId()));
        this.requireOwnedBy(flight, airlineResponse.getId());

        // get aircraft data from airline-core-service
        final AircraftResponse aircraftResponse = this.airlineClient.getAircraftById(flight.getAircraftId()).getData();

        final FlightInstance saved = this.flightInstanceRepository.save(
                this.toEntity(flightInstanceRequest, flight, aircraftResponse));

        // todo: create seat instances
        // publish kafka event, seat service consume that and create seat instance
        return this.convertToFlightInstanceResponse(saved, flight);
    }

    @Override
    @Transactional
    public void createFlightInstances(final UUID airlineId,
            final List<FlightInstanceRequest> flightInstanceRequests) {
        if (flightInstanceRequests.isEmpty()) {
            return;
        }
        final List<UUID> flightIds = flightInstanceRequests.stream()
                .map(FlightInstanceRequest::getFlightId).distinct().toList();
        final Map<UUID, Flight> flightById = this.flightRepository.findAllById(flightIds).stream()
                .collect(Collectors.toMap(Flight::getId, Function.identity()));

        for (final UUID flightId : flightIds) {
            final Flight flight = flightById.get(flightId);
            if (flight == null) {
                throw new ResourceNotFoundException("Flight", "id", flightId);
            }
            this.requireOwnedBy(flight, airlineId);
        }

        final List<UUID> aircraftIds = flightById.values().stream().map(Flight::getAircraftId).distinct().toList();
        final Map<UUID, AircraftResponse> aircraftById = this.airlineClient.getAircraftsByIds(aircraftIds).getData();

        final List<FlightInstance> flightInstances = new ArrayList<>(flightInstanceRequests.size());
        for (final FlightInstanceRequest request : flightInstanceRequests) {
            final Flight flight = flightById.get(request.getFlightId());
            flightInstances.add(this.toEntity(request, flight, aircraftById.get(flight.getAircraftId())));
        }
        this.flightInstanceRepository.saveAll(flightInstances);
    }

    private void requireOwnedBy(final Flight flight, final UUID airlineId) {
        if (!airlineId.equals(flight.getAirlineId())) {
            throw new ResourceNotFoundException("Flight", "id", flight.getId());
        }
    }

    private FlightInstance toEntity(final FlightInstanceRequest request, final Flight flight,
            final AircraftResponse aircraftResponse) {
        final FlightInstance flightInstance = FlightInstanceMapper.toEntity(request, flight);
        flightInstance.setTotalSeats(aircraftResponse.getTotalSeats());
        flightInstance.setAvailableSeats(aircraftResponse.getTotalSeats());
        return flightInstance;
    }

    @Override
    public FlightInstanceResponse getFlightInstanceById(final UUID id) {
        final FlightInstance flightInstance = this.flightInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightInstance", "id", id));
        final Flight flight = this.flightRepository.findById(flightInstance.getFlightId()).orElse(null);
        return this.convertToFlightInstanceResponse(flightInstance, flight);
    }

    @Override
    public Page<FlightInstanceResponse> getByAirlineId(final UUID userId, final UUID departureAirportId,
                                                       final UUID arrivalAirportId, final UUID flightId, final LocalDate onDate, final Pageable pageable) {

        // fetch by owner id
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
        final LocalDateTime start = onDate != null ? onDate.atStartOfDay() : null;
        final LocalDateTime end = onDate != null ? onDate.plusDays(1).atStartOfDay() : null;

        final Page<FlightInstance> page = this.flightInstanceRepository.findByAirlineId(airlineResponse.getId(),
                departureAirportId, arrivalAirportId, flightId, start, end, pageable);

        // Batch-load Flight cho cả trang thay vì fetch từng cái trong Page.map() (N+1)
        final List<UUID> flightIds = page.getContent().stream()
                .map(FlightInstance::getFlightId).distinct().toList();
        final Map<UUID, Flight> flightById = this.flightRepository.findAllById(flightIds)
                .stream().collect(Collectors.toMap(Flight::getId, Function.identity()));

        return page.map(fi -> this.convertToFlightInstanceResponse(fi, flightById.get(fi.getFlightId())));
    }

    @Override
    @Transactional
    public FlightInstanceResponse updateFlightInstance(final UUID id,
                                                       final FlightInstanceRequest flightInstanceRequest) {
        final FlightInstance existing = this.flightInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightInstance", "id", id));
        FlightInstanceMapper.updateEntity(flightInstanceRequest, existing);
        final FlightInstance saved = this.flightInstanceRepository.save(existing);
        final Flight flight = this.flightRepository.findById(saved.getFlightId()).orElse(null);
        return this.convertToFlightInstanceResponse(saved, flight);
    }

    @Override
    @Transactional
    public void deleteFlightInstance(final UUID id) {
        if (!this.flightInstanceRepository.existsById(id)) {
            throw new ResourceNotFoundException("FlightInstance", "id", id);
        }
        this.flightInstanceRepository.deleteById(id);
    }

    private FlightInstanceResponse convertToFlightInstanceResponse(final FlightInstance flightInstance,
                                                                   final Flight flight) {

        // service to service communication
        final AirlineResponse airlineResponse = flightInstance.getAirlineId() != null
                ? airlineClient.getAirlineById(flightInstance.getAirlineId()).getData()
                : null;

        final UUID depId = flightInstance.getDepartureAirportId() != null
                ? flightInstance.getDepartureAirportId()
                : (flight != null ? flight.getDepartureAirportId() : null);
        final UUID arrId = flightInstance.getArrivalAirportId() != null
                ? flightInstance.getArrivalAirportId()
                : (flight != null ? flight.getArrivalAirportId() : null);

        final AirportResponse departureAirport = depId != null
                ? locationClient.getAirportById(depId).getData()
                : null;
        final AirportResponse arrivalAirport = arrId != null
                ? locationClient.getAirportById(arrId).getData()
                : null;
        final AircraftResponse aircraftResponse = flight != null && flight.getAircraftId() != null
                ? airlineClient.getAircraftById(flight.getAircraftId()).getData()
                : null;

        return FlightInstanceMapper.toResponse(flightInstance, flight, aircraftResponse, airlineResponse,
                departureAirport, arrivalAirport);
    }
}
