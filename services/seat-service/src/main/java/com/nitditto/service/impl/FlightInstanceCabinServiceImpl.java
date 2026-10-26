package com.nitditto.service.impl;

import com.nitditto.enums.SeatAvailabilityStatus;
import com.nitditto.enums.SeatType;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FlightInstanceCabinMapper;
import com.nitditto.mapper.FlightInstanceCabinMapper.FlightInstanceCabinContext;
import com.nitditto.model.CabinClass;
import com.nitditto.model.FlightInstanceCabin;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatInstance;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.FlightInstanceCabinRequest;
import com.nitditto.payload.response.FlightInstanceCabinResponse;
import com.nitditto.repository.CabinClassRepository;
import com.nitditto.repository.FlightInstanceCabinRepository;
import com.nitditto.repository.SeatInstanceRepository;
import com.nitditto.repository.SeatMapRepository;
import com.nitditto.repository.SeatRepository;
import com.nitditto.service.FlightInstanceCabinService;
import com.nitditto.service.SeatHoldPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightInstanceCabinServiceImpl implements FlightInstanceCabinService {

    private final FlightInstanceCabinRepository flightInstanceCabinRepository;
    private final CabinClassRepository cabinClassRepository;
    private final SeatMapRepository seatMapRepository;
    private final SeatRepository seatRepository;
    private final SeatInstanceRepository seatInstanceRepository;
    private final Clock clock;

    @Override
    @Transactional
    public FlightInstanceCabinResponse createFlightInstanceCabin(final FlightInstanceCabinRequest request) {
        final CabinClass cabinClass = this.cabinClassRepository.findById(request.getCabinClassId())
                .orElseThrow(() -> new ResourceNotFoundException("CabinClass", "id", request.getCabinClassId()));

        final SeatMap seatMap = this.seatMapRepository.findByCabinClassId(cabinClass.getId())
                .orElseThrow(() -> new ResourceNotFoundException("SeatMap", "cabinClassId", cabinClass.getId()));

        if (this.flightInstanceCabinRepository.existsByFlightInstanceIdAndCabinClassId(
                request.getFlightInstanceId(), cabinClass.getId())) {
            throw new BusinessException("Flight instance cabin already exists for this cabin class");
        }

        final List<Seat> seats = this.seatRepository.findBySeatMapId(seatMap.getId());
        if (seats.isEmpty()) {
            throw new BusinessException("No seats found for seat map of cabin class: " + cabinClass.getId());
        }

        final FlightInstanceCabin flightInstanceCabin = FlightInstanceCabin.builder()
                .flightInstanceId(request.getFlightInstanceId())
                .cabinClassId(cabinClass.getId())
                .totalSeats(seats.size())
                .bookedSeats(0)
                .baseFare(request.getBaseFare())
                .windowSurcharge(request.getWindowSurcharge())
                .aisleSurcharge(request.getAisleSurcharge())
                .taxesAndFees(request.getTaxesAndFees())
                .airlineFees(request.getAirlineFees())
                .currentPrice(request.getCurrentPrice())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        final FlightInstanceCabin savedFlightInstanceCabin = this.flightInstanceCabinRepository.save(flightInstanceCabin);

        final List<SeatInstance> seatInstances = this.generateSeatInstances(
                savedFlightInstanceCabin, request.getFlightId(), seatMap, seats,
                request.getWindowSurcharge(), request.getAisleSurcharge());

        final Map<UUID, Seat> seatById = seats.stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));

        return FlightInstanceCabinMapper.toResponse(new FlightInstanceCabinContext(
                savedFlightInstanceCabin, cabinClass, seatMap, seatInstances, seatById));
    }

    private List<SeatInstance> generateSeatInstances(final FlightInstanceCabin flightInstanceCabin,
            final UUID flightId, final SeatMap seatMap, final List<Seat> seats,
            final Double windowSurcharge, final Double aisleSurcharge) {
        final double cabinTotalPrice = flightInstanceCabin.getTotalCabinPrice();

        final List<SeatInstance> seatInstances = seats.stream()
                .map(seat -> {
                    final double premiumSuperCharge = getPremiumSuperCharge(
                            seat.getSeatType(), windowSurcharge, aisleSurcharge);
                    return SeatInstance.builder()
                            .flightId(flightId)
                            .flightInstanceId(flightInstanceCabin.getFlightInstanceId())
                            .flightInstanceCabinId(flightInstanceCabin.getId())
                            .seatId(seat.getId())
                            .seatMapId(seatMap.getId())
                            .status(SeatAvailabilityStatus.AVAILABLE)
                            .isAvailable(true)
                            .isBooked(false)
                            .premiumSuperCharge(premiumSuperCharge)
                            .fare(cabinTotalPrice + premiumSuperCharge)
                            .build();
                })
                .toList();

        this.seatInstanceRepository.saveAll(seatInstances);
        return seatInstances;
    }

    private static double getPremiumSuperCharge(final SeatType seatType, final Double windowSurcharge,
            final Double aisleSurcharge) {
        if (seatType == null) return 0.0;
        return switch (seatType) {
            case WINDOW -> windowSurcharge != null ? windowSurcharge : 0.0;
            case AISLE -> aisleSurcharge != null ? aisleSurcharge : 0.0;
            default -> 0.0;
        };
    }

    @Override
    public FlightInstanceCabinResponse getFlightInstanceCabinById(final UUID id) {
        final FlightInstanceCabin flightInstanceCabin = this.flightInstanceCabinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightInstanceCabin", "id", id));

        return this.toDetailResponse(flightInstanceCabin);
    }

    @Override
    public Page<FlightInstanceCabinResponse> getByFlightInstanceId(final UUID flightInstanceId,
            final Pageable pageable) {
        final Page<FlightInstanceCabin> page = this.flightInstanceCabinRepository
                .findByFlightInstanceId(flightInstanceId, pageable);

        // List view: chỉ số liệu tổng hợp sẵn có trên entity, KHÔNG đụng bảng seat_instances.
        // Trang có thể trải nhiều cabinClassId khác nhau -> batch đúng công thức Map<UUID,T>.
        final List<UUID> cabinClassIds = page.getContent().stream()
                .map(FlightInstanceCabin::getCabinClassId).distinct().toList();
        final Map<UUID, CabinClass> cabinClassById = this.cabinClassRepository.findAllById(cabinClassIds)
                .stream().collect(Collectors.toMap(CabinClass::getId, Function.identity()));

        return page.map(fic -> FlightInstanceCabinMapper.toResponse(new FlightInstanceCabinContext(
                fic, cabinClassById.get(fic.getCabinClassId()), null, null, null)));
    }

    @Override
    public FlightInstanceCabinResponse getByFlightInstanceIdAndCabinClassId(final UUID flightInstanceId,
            final UUID cabinClassId, final UUID requesterId) {
        final FlightInstanceCabin flightInstanceCabin = this.flightInstanceCabinRepository
                .findByFlightInstanceIdAndCabinClassId(flightInstanceId, cabinClassId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FlightInstanceCabin", "flightInstanceId+cabinClassId",
                        flightInstanceId + "/" + cabinClassId));

        final FlightInstanceCabinResponse response = this.toDetailResponse(flightInstanceCabin);
        this.overlayHolds(response, flightInstanceCabin.getId(), requesterId);
        return response;
    }

    // Ghế đang bị người khác giữ chỗ (chưa hết hạn) hiển thị là HELD để không ai chọn trùng trong lúc họ thanh toán.
    private void overlayHolds(final FlightInstanceCabinResponse response, final UUID flightInstanceCabinId,
            final UUID requesterId) {
        if (response.getSeats() == null) {
            return;
        }
        final Instant now = this.clock.instant();
        final Map<UUID, SeatInstance> entityById = this.seatInstanceRepository
                .findByFlightInstanceCabinId(flightInstanceCabinId).stream()
                .collect(Collectors.toMap(SeatInstance::getId, Function.identity()));
        for (final var seat : response.getSeats()) {
            final SeatInstance entity = entityById.get(seat.getId());
            if (entity != null && seat.getStatus() == SeatAvailabilityStatus.AVAILABLE
                    && SeatHoldPolicy.isHeldByOther(entity, requesterId, now)) {
                seat.setStatus(SeatAvailabilityStatus.HELD);
                seat.setIsAvailable(false);
            }
        }
    }

    /** 1 FlightInstanceCabin + đầy đủ seat instances (5 query cố định, không phụ thuộc số ghế). */
    private FlightInstanceCabinResponse toDetailResponse(final FlightInstanceCabin flightInstanceCabin) {
        final CabinClass cabinClass = this.cabinClassRepository.findById(flightInstanceCabin.getCabinClassId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CabinClass", "id", flightInstanceCabin.getCabinClassId()));
        final SeatMap seatMap = this.seatMapRepository.findByCabinClassId(cabinClass.getId()).orElse(null);

        final List<SeatInstance> seatInstances = this.seatInstanceRepository
                .findByFlightInstanceCabinId(flightInstanceCabin.getId());
        final List<UUID> seatIds = seatInstances.stream().map(SeatInstance::getSeatId).distinct().toList();
        final Map<UUID, Seat> seatById = this.seatRepository.findAllById(seatIds)
                .stream().collect(Collectors.toMap(Seat::getId, Function.identity()));

        return FlightInstanceCabinMapper.toResponse(new FlightInstanceCabinContext(
                flightInstanceCabin, cabinClass, seatMap, seatInstances, seatById));
    }

    @Override
    @Transactional
    public FlightInstanceCabinResponse updateFlightInstanceCabin(final UUID id,
            final FlightInstanceCabinRequest request) {
        final FlightInstanceCabin flightInstanceCabin = this.flightInstanceCabinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlightInstanceCabin", "id", id));

        final UUID existingCabinClassId = flightInstanceCabin.getCabinClassId();
        CabinClass cabinClass = this.cabinClassRepository.findById(existingCabinClassId)
                .orElseThrow(() -> new ResourceNotFoundException("CabinClass", "id", existingCabinClassId));
        SeatMap seatMap = this.seatMapRepository.findByCabinClassId(existingCabinClassId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatMap", "cabinClassId", existingCabinClassId));

        final boolean cabinClassChanged = request.getCabinClassId() != null
                && !request.getCabinClassId().equals(flightInstanceCabin.getCabinClassId());

        List<Seat> newSeats = null;
        if (cabinClassChanged) {
            final UUID newCabinClassId = request.getCabinClassId();
            if (this.flightInstanceCabinRepository.existsByFlightInstanceIdAndCabinClassIdAndIdNot(
                    flightInstanceCabin.getFlightInstanceId(), newCabinClassId, id)) {
                throw new BusinessException("Flight instance cabin already exists for this cabin class");
            }
            cabinClass = this.cabinClassRepository.findById(newCabinClassId)
                    .orElseThrow(() -> new ResourceNotFoundException("CabinClass", "id", newCabinClassId));
            seatMap = this.seatMapRepository.findByCabinClassId(newCabinClassId)
                    .orElseThrow(() -> new ResourceNotFoundException("SeatMap", "cabinClassId", newCabinClassId));
            newSeats = this.seatRepository.findBySeatMapId(seatMap.getId());
            if (newSeats.isEmpty()) {
                throw new BusinessException("No seats found for seat map of cabin class: " + newCabinClassId);
            }
        }

        FlightInstanceCabinMapper.updateEntity(request, flightInstanceCabin);
        if (cabinClassChanged) {
            flightInstanceCabin.setCabinClassId(cabinClass.getId());
            flightInstanceCabin.setTotalSeats(newSeats.size());
            flightInstanceCabin.setBookedSeats(0);
        }
        final FlightInstanceCabin updatedFlightInstanceCabin = this.flightInstanceCabinRepository.save(flightInstanceCabin);

        List<SeatInstance> seatInstances;
        Map<UUID, Seat> seatById;
        if (cabinClassChanged) {
            // Cabin class đổi -> layout đổi -> seat instance cũ không còn khớp, xoá và sinh lại.
            this.seatInstanceRepository.deleteByFlightInstanceCabinId(id);
            this.seatInstanceRepository.flush();
            seatInstances = this.generateSeatInstances(updatedFlightInstanceCabin, request.getFlightId(),
                    seatMap, newSeats, request.getWindowSurcharge(), request.getAisleSurcharge());
            seatById = newSeats.stream().collect(Collectors.toMap(Seat::getId, Function.identity()));
        } else {
            seatInstances = this.seatInstanceRepository.findByFlightInstanceCabinId(id);
            final List<UUID> seatIds = seatInstances.stream().map(SeatInstance::getSeatId).distinct().toList();
            seatById = this.seatRepository.findAllById(seatIds)
                    .stream().collect(Collectors.toMap(Seat::getId, Function.identity()));
        }

        return FlightInstanceCabinMapper.toResponse(new FlightInstanceCabinContext(
                updatedFlightInstanceCabin, cabinClass, seatMap, seatInstances, seatById));
    }

    @Override
    @Transactional
    public void deleteFlightInstanceCabin(final UUID id) {
        if (!this.flightInstanceCabinRepository.existsById(id)) {
            throw new ResourceNotFoundException("FlightInstanceCabin", "id", id);
        }
        this.seatInstanceRepository.deleteByFlightInstanceCabinId(id);
        this.flightInstanceCabinRepository.deleteById(id);
    }
}
