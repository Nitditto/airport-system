package com.nitditto.service.impl;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.payload.event.FlightInstanceCreatedEvent;
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
import com.nitditto.service.integration.FlightInstanceAssembler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightInstanceServiceImpl implements FlightInstanceService {

    private static final int MAX_PUBLIC_PAGE_SIZE = 100;

    private final FlightRepository flightRepository;
    private final FlightInstanceRepository flightInstanceRepository;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;
    private final FlightInstanceAssembler flightInstanceAssembler;
    // Event được FlightInstanceEventProducer đẩy lên Kafka sau khi transaction commit.
    private final ApplicationEventPublisher eventPublisher;

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

        final FlightInstance flightInstance = this.flightInstanceRepository.save(
                this.toEntity(flightInstanceRequest, flight, aircraftResponse));

        flightInstance.setTotalSeats(aircraftResponse.getTotalSeats());
        flightInstance.setAvailableSeats(aircraftResponse.getTotalSeats());

        // seat-service nghe event này để sinh seat instance cho chuyến bay.
        this.publishCreated(flightInstance, flight);

        return this.convertToFlightInstanceResponse(flightInstance, flight);
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
        for (final FlightInstance flightInstance : flightInstances) {
            this.publishCreated(flightInstance, flightById.get(flightInstance.getFlightId()));
        }
    }

    private void publishCreated(final FlightInstance flightInstance, final Flight flight) {
        this.eventPublisher.publishEvent(FlightInstanceCreatedEvent.of(
                flightInstance.getId(), flight.getAircraftId(), flight.getId()));
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

        // Batch-load Flight + dữ liệu tham chiếu cho cả trang thay vì gọi từng chuyến
        // trong Page.map() (N+1)
        final Map<UUID, FlightInstanceResponse> responseById = this.toResponses(page.getContent());
        return page.map(fi -> responseById.get(fi.getId()));
    }

    @Override
    public Page<FlightInstanceResponse> searchPublic(final UUID departureAirportId, final UUID arrivalAirportId,
            final LocalDate onDate, final int passengers,
            final Pageable pageable) {
        // Endpoint công khai: chặn size quá lớn để một request không kéo cả bảng.
        final Pageable safePageable = PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PUBLIC_PAGE_SIZE), pageable.getSort());

        // Giờ khởi hành lưu theo giờ địa phương của sân bay đi, nên "đã cất cánh chưa"
        // phải so với giờ hiện
        // tại ở múi giờ đó, không phải múi giờ của server/container (thường là UTC).
        // Sân bay không tồn tại
        // thì lời gọi này ném 404 luôn.
        final AirportResponse departureAirport = this.locationClient.getAirportById(departureAirportId).getData();
        final ZoneId zone = departureAirport.getTimeZone() != null
                ? departureAirport.getTimeZone()
                : ZoneId.systemDefault();

        final Page<FlightInstance> page = this.flightInstanceRepository.searchOpenForSale(departureAirportId,
                arrivalAirportId, onDate.atStartOfDay(), onDate.plusDays(1).atStartOfDay(),
                LocalDateTime.now(zone), Math.max(1, passengers), safePageable);
        if (page.isEmpty()) {
            return Page.empty(safePageable);
        }

        // Số lời gọi cố định, không phụ thuộc số chuyến (CLAUDE.md §7): hãng, máy bay,
        // sân bay
        // mỗi loại một lời gọi batch-by-ids.
        final Map<UUID, FlightInstanceResponse> responseById = this.toResponses(page.getContent());
        return page.map(fi -> responseById.get(fi.getId()));
    }

    @Override
    public Map<UUID, FlightInstanceResponse> getByIds(final List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return this.toResponses(this.flightInstanceRepository.findAllById(ids.stream().distinct().toList()));
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
        final Map<UUID, Flight> flightById = flight != null ? Map.of(flight.getId(), flight) : Map.of();
        return this.toResponses(List.of(flightInstance), flightById).get(flightInstance.getId());
    }

    /** Map theo id chuyến, giữ thứ tự đầu vào. Nạp Flight bằng một query IN. */
    private Map<UUID, FlightInstanceResponse> toResponses(final List<FlightInstance> instances) {
        if (instances.isEmpty()) {
            return Map.of();
        }
        final List<UUID> flightIds = instances.stream().map(FlightInstance::getFlightId).distinct().toList();
        final Map<UUID, Flight> flightById = this.flightRepository.findAllById(flightIds).stream()
                .collect(Collectors.toMap(Flight::getId, Function.identity()));
        return this.toResponses(instances, flightById);
    }

    private Map<UUID, FlightInstanceResponse> toResponses(final List<FlightInstance> instances,
            final Map<UUID, Flight> flightById) {
        return this.flightInstanceAssembler.assemble(instances, flightById);
    }
}
