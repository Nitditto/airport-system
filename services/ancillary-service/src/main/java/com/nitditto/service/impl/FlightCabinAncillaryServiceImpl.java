package com.nitditto.service.impl;

import com.nitditto.enums.AncillaryType;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.AncillaryMapper;
import com.nitditto.mapper.FlightCabinAncillaryMapper;
import com.nitditto.mapper.InsuranceCoverageMapper;
import com.nitditto.model.Ancillary;
import com.nitditto.model.FlightCabinAncillary;
import com.nitditto.model.InsuranceCoverage;
import com.nitditto.payload.request.FlightCabinAncillaryRequest;
import com.nitditto.payload.response.AncillaryResponse;
import com.nitditto.payload.response.FlightCabinAncillaryResponse;
import com.nitditto.payload.response.InsuranceCoverageResponse;
import com.nitditto.repository.AncillaryRepository;
import com.nitditto.repository.FlightCabinAncillaryRepository;
import com.nitditto.repository.InsuranceCoverageRepository;
import com.nitditto.service.FlightCabinAncillaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FlightCabinAncillaryServiceImpl implements FlightCabinAncillaryService {

    private static final int MAX_BATCH_SIZE = 200;

    private final FlightCabinAncillaryRepository flightCabinAncillaryRepository;
    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;

    @Override
    @Transactional
    public FlightCabinAncillaryResponse createFlightCabinAncillary(final FlightCabinAncillaryRequest request) {
        if (!this.ancillaryRepository.existsById(request.getAncillaryId())) {
            throw new ResourceNotFoundException("Ancillary", "id", request.getAncillaryId());
        }
        if (this.flightCabinAncillaryRepository.existsByFlightIdAndCabinClassIdAndAncillaryId(
                request.getFlightId(), request.getCabinClassId(), request.getAncillaryId())) {
            throw new BusinessException("Ancillary %s is already configured for flight %s / cabin %s"
                    .formatted(request.getAncillaryId(), request.getFlightId(), request.getCabinClassId()));
        }

        final boolean includedInFare = Boolean.TRUE.equals(request.getIncludedInFare());
        final FlightCabinAncillary entity = FlightCabinAncillary.builder()
                .flightId(request.getFlightId())
                .cabinClassId(request.getCabinClassId())
                .ancillaryId(request.getAncillaryId())
                .available(request.getAvailable())
                .maxQuantity(request.getMaxQuantity())
                .price(resolvePrice(includedInFare, request.getAvailable(), request.getPrice()))
                .includedInFare(includedInFare)
                .build();

        return buildResponse(this.flightCabinAncillaryRepository.save(entity));
    }

    @Override
    public FlightCabinAncillaryResponse getById(final UUID id) {
        final FlightCabinAncillary entity = this.flightCabinAncillaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight Cabin Ancillary", "id", id));
        return buildResponse(entity);
    }

    @Override
    public List<FlightCabinAncillaryResponse> getByFlightAndCabinClass(final UUID flightId, final UUID cabinClassId) {
        return buildResponses(
                this.flightCabinAncillaryRepository.findByFlightIdAndCabinClassId(flightId, cabinClassId));
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByIds(final List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(
                    "Cannot request more than %d flight cabin ancillaries at once".formatted(MAX_BATCH_SIZE));
        }
        return buildResponses(
                this.flightCabinAncillaryRepository.findAllById(ids.stream().distinct().toList()));
    }

    @Override
    public FlightCabinAncillaryResponse getByFlightIdAndCabinClassIdAndType(final UUID flightId,
            final UUID cabinClassId, final AncillaryType ancillaryType) {
        final List<FlightCabinAncillary> matches = this.flightCabinAncillaryRepository
                .findByFlightCabinAndAncillaryType(flightId, cabinClassId, ancillaryType);
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("Flight Cabin Ancillary", "type", ancillaryType);
        }
        // Không có ràng buộc duy nhất trên (flight, cabin, type) — nhiều ancillary cùng type
        // vẫn hợp lệ. Trả bản ghi đầu; caller cần tất cả thì dùng getAllBy...Type.
        return buildResponse(matches.get(0));
    }

    @Override
    public List<FlightCabinAncillaryResponse> getAllByFlightIdAndCabinClassIdAndType(final UUID flightId,
            final UUID cabinClassId, final AncillaryType ancillaryType) {
        return buildResponses(this.flightCabinAncillaryRepository
                .findByFlightCabinAndAncillaryType(flightId, cabinClassId, ancillaryType));
    }

    @Override
    @Transactional
    public FlightCabinAncillaryResponse update(final UUID id, final FlightCabinAncillaryRequest request) {
        final FlightCabinAncillary entity = this.flightCabinAncillaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight Cabin Ancillary", "id", id));

        // (flightId, cabinClassId, ancillaryId) là danh tính của bản ghi — không cho đổi qua update.
        final boolean includedInFare = Boolean.TRUE.equals(request.getIncludedInFare());
        entity.setAvailable(request.getAvailable());
        entity.setMaxQuantity(request.getMaxQuantity());
        entity.setIncludedInFare(includedInFare);
        entity.setPrice(resolvePrice(includedInFare, request.getAvailable(), request.getPrice()));

        return buildResponse(this.flightCabinAncillaryRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(final UUID id) {
        if (!this.flightCabinAncillaryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Flight Cabin Ancillary", "id", id);
        }
        this.flightCabinAncillaryRepository.deleteById(id);
    }

    @Override
    public Double calculateAncillaryPrice(final List<UUID> flightCabinAncillaryIds) {
        if (flightCabinAncillaryIds == null || flightCabinAncillaryIds.isEmpty()) {
            return 0.0;
        }
        if (flightCabinAncillaryIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(
                    "Cannot price more than %d ancillaries at once".formatted(MAX_BATCH_SIZE));
        }
        final List<FlightCabinAncillary> entities = this.flightCabinAncillaryRepository
                .findAllById(flightCabinAncillaryIds.stream().distinct().toList());

        double total = 0.0;
        for (final FlightCabinAncillary entity : entities) {
            // includedInFare => đã nằm trong giá vé, không cộng thêm; price null => bỏ qua.
            if (Boolean.TRUE.equals(entity.getIncludedInFare()) || entity.getPrice() == null) {
                continue;
            }
            total += entity.getPrice();
        }
        return total;
    }

    // Business rule: ancillary kèm trong giá vé thì giá lẻ = 0; ancillary bán thêm mà đang mở
    // bán (available) thì bắt buộc có giá.
    private Double resolvePrice(final boolean includedInFare, final Boolean available, final Double price) {
        if (includedInFare) {
            return 0.0;
        }
        if (Boolean.TRUE.equals(available) && price == null) {
            throw new BusinessException(
                    "Price is required for a purchasable ancillary that is not included in fare");
        }
        return price;
    }

    private FlightCabinAncillaryResponse buildResponse(final FlightCabinAncillary entity) {
        return buildResponses(List.of(entity)).get(0);
    }

    private List<FlightCabinAncillaryResponse> buildResponses(final List<FlightCabinAncillary> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        final List<UUID> ancillaryIds = entities.stream()
                .map(FlightCabinAncillary::getAncillaryId).distinct().toList();

        final Map<UUID, Ancillary> ancillaryById = this.ancillaryRepository.findAllById(ancillaryIds)
                .stream().collect(Collectors.toMap(Ancillary::getId, Function.identity()));

        final Map<UUID, List<InsuranceCoverageResponse>> coveragesByAncillaryId =
                this.insuranceCoverageRepository.findByAncillaryIdIn(ancillaryIds).stream()
                        .collect(Collectors.groupingBy(
                                InsuranceCoverage::getAncillaryId,
                                Collectors.mapping(
                                        ic -> InsuranceCoverageMapper.toResponse(
                                                ic, ancillaryById.get(ic.getAncillaryId())),
                                        Collectors.toList())));

        final List<FlightCabinAncillaryResponse> responses = new ArrayList<>(entities.size());
        for (final FlightCabinAncillary entity : entities) {
            final Ancillary ancillary = ancillaryById.get(entity.getAncillaryId());
            final AncillaryResponse ancillaryResponse = ancillary == null ? null
                    : AncillaryMapper.toResponse(ancillary,
                            coveragesByAncillaryId.getOrDefault(entity.getAncillaryId(), List.of()));
            responses.add(FlightCabinAncillaryMapper.toResponse(entity, ancillaryResponse));
        }
        return responses;
    }
}
