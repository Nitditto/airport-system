package com.nitditto.service.impl;

import com.nitditto.client.AirlineClient;
import com.nitditto.enums.FlightStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FlightScheduleMapper;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightSchedule;
import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightScheduleResponse;
import com.nitditto.repository.FlightRepository;
import com.nitditto.repository.FlightScheduleRepository;
import com.nitditto.service.FlightInstanceService;
import com.nitditto.service.FlightScheduleService;
import com.nitditto.service.integration.ReferenceData;
import com.nitditto.service.integration.ReferenceDataLoader;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightScheduleServiceImpl implements FlightScheduleService {

    private final FlightRepository flightRepository;
    private final FlightScheduleRepository flightScheduleRepository;
    private final FlightInstanceService flightInstanceService;
    private final AirlineClient airlineClient;
    private final ReferenceDataLoader referenceDataLoader;

    @Transactional(rollbackFor = Exception.class)
    @Override
        public FlightScheduleResponse createFlightSchedule(final UUID userId,
            final FlightScheduleRequest flightScheduleRequest) {

                AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
        if (flightScheduleRequest.getOperatingDays() == null
                || flightScheduleRequest.getOperatingDays().isEmpty()) {
            throw new BusinessException("Operating days cannot be empty");
        }

        final Flight flight = this.flightRepository.findById(flightScheduleRequest.getFlightId())
                .orElseThrow(() -> new ResourceNotFoundException("Flight", "id",
                        flightScheduleRequest.getFlightId()));

        if (flightScheduleRequest.getEndDate().isBefore(flightScheduleRequest.getStartDate())) {
            throw new BusinessException("End date cannot be before start date");
        }
        if (!airlineResponse.getId().equals(flight.getAirlineId())) {
            throw new ResourceNotFoundException("Flight", "id", flight.getId());
        }
        final FlightSchedule flightSchedule = FlightScheduleMapper.toEntity(flightScheduleRequest, flight);
        final FlightSchedule savedSchedule = this.flightScheduleRepository.save(flightSchedule);

        // create flight instance for saved scheduled
        // 11/03 to 10/04
        final Set<DayOfWeek> operatingDays = savedSchedule.getOperatingDays();
        final LocalDate startDate = savedSchedule.getStartDate();
        final LocalDate endDate = savedSchedule.getEndDate();

        final List<FlightInstanceRequest> flightInstanceRequests = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (operatingDays.contains(date.getDayOfWeek())) {
                LocalDateTime departureDateTime = LocalDateTime.of(date, savedSchedule.getDepartureTime());
                LocalDateTime arrivalDateTime = LocalDateTime.of(date, savedSchedule.getArrivalTime());
                if (arrivalDateTime.isBefore(departureDateTime)) {
                    arrivalDateTime = arrivalDateTime.plusDays(1);
                }
                flightInstanceRequests.add(FlightInstanceRequest.builder()
                        .scheduleId(savedSchedule.getId())
                        .flightId(flight.getId())
                        .arrivalAirportId(flight.getArrivalAirportId())
                        .departureAirportId(flight.getDepartureAirportId())
                        .status(FlightStatus.SCHEDULED)
                        .departureDateTime(departureDateTime)
                        .arrivalDateTime(arrivalDateTime)
                        .build());
            }
        }
        this.flightInstanceService.createFlightInstances(airlineResponse.getId(), flightInstanceRequests);
        return FlightScheduleMapper.toResponse(savedSchedule, flight,
                AirportResponse.builder().id(flight.getArrivalAirportId()).build(),
                AirportResponse.builder().id(flight.getDepartureAirportId()).build());
    }

    @Override
    public FlightScheduleResponse getFlightScheduleById(final UUID id) {
        final FlightSchedule flightSchedule = this.flightScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightSchedule", "id", id));
        return this.toResponses(List.of(flightSchedule)).get(0);
    }

    @Override
    public List<FlightScheduleResponse> getFlightScheduleByAirline(final UUID userId) {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
        final List<UUID> flightIds = this.flightRepository.findIdsByAirlineId(airlineResponse.getId());
        final List<FlightSchedule> schedules = this.flightScheduleRepository.findByFlightIdIn(flightIds);
        return this.toResponses(schedules);
    }

    @Override
    @Transactional
    public FlightScheduleResponse updateFlightSchedule(final UUID id,
                                                       final FlightScheduleRequest flightScheduleRequest) {
        final FlightSchedule flightSchedule = this.flightScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightSchedule", "id", id));
        FlightScheduleMapper.updateEntity(flightScheduleRequest, flightSchedule);
        final FlightSchedule updatedSchedule = this.flightScheduleRepository.save(flightSchedule);

        return this.toResponses(List.of(updatedSchedule)).get(0);
    }

    @Override
    @Transactional
    public void deleteFlightScheduleById(final UUID id) {
        if (!this.flightScheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("FlightSchedule", "id", id);
        }
        this.flightScheduleRepository.deleteById(id);
    }

    private List<FlightScheduleResponse> toResponses(final List<FlightSchedule> schedules) {
        if (schedules.isEmpty()) {
            return List.of();
        }

        final List<UUID> flightIds = schedules.stream().map(FlightSchedule::getFlightId).distinct().toList();
        final Map<UUID, Flight> flightById = this.flightRepository.findAllById(flightIds)
                .stream().collect(Collectors.toMap(Flight::getId, Function.identity()));

        // Một lời gọi batch cho mọi sân bay của danh sách (CLAUDE.md §7)
        final List<UUID> airportIds = new ArrayList<>(schedules.size() * 2);
        for (final FlightSchedule schedule : schedules) {
            airportIds.add(schedule.getDepartureAirportId());
            airportIds.add(schedule.getArrivalAirportId());
        }
        final ReferenceData referenceData = this.referenceDataLoader.load(List.of(), List.of(), airportIds);

        final List<FlightScheduleResponse> responses = new ArrayList<>(schedules.size());
        for (final FlightSchedule schedule : schedules) {
            responses.add(FlightScheduleMapper.toResponse(schedule, flightById.get(schedule.getFlightId()),
                    airportOrIdOnly(referenceData, schedule.getArrivalAirportId()),
                    airportOrIdOnly(referenceData, schedule.getDepartureAirportId())));
        }
        return responses;
    }

    /** Giữ hành vi cũ khi location-service không còn sân bay đó: vẫn trả id để FE không mất liên kết. */
    private static AirportResponse airportOrIdOnly(final ReferenceData referenceData, final UUID airportId) {
        final AirportResponse airport = referenceData.airport(airportId);
        if (airport != null || airportId == null) {
            return airport;
        }
        return AirportResponse.builder().id(airportId).build();
    }
}
