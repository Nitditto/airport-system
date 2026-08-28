package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FareMapper;
import com.nitditto.model.BaggagePolicy;
import com.nitditto.model.Fare;
import com.nitditto.model.FareRules;
import com.nitditto.payload.request.FareRequest;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.repository.BaggagePolicyRepository;
import com.nitditto.repository.FareRepository;
import com.nitditto.repository.FareRuleRepository;
import com.nitditto.service.FareService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FareServiceImpl implements FareService {

    private final FareRepository fareRepository;
    private final FareRuleRepository fareRuleRepository;
    private final BaggagePolicyRepository baggagePolicyRepository;

    @Override
    @Transactional
    public FareResponse createFare(final FareRequest request) {
        if (this.fareRepository.existsByFlightIdAndCabinClassIdAndName(request.getFlightId(),
                request.getCabinClassId(), request.getName())) {
            throw new BusinessException("Fare already exist with provided name");
        }
        final Fare fare = FareMapper.toEntity(request);
        final Fare savedFare = this.fareRepository.save(fare);
        // Fare mới tạo chưa thể có FareRules/BaggagePolicy (2 endpoint riêng tạo sau) -> khỏi query thêm
        return FareMapper.toResponse(savedFare, null, null);
    }

    @Override
    public FareResponse getFareById(final UUID id) {
        final Fare fare = this.fareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fare", "id", id));
        return this.toResponses(List.of(fare)).get(0);
    }

    @Override
    public List<FareResponse> getFaresByFlightIdAndCabinClassId(final UUID flightId, final UUID cabinClassId) {
        return this.toResponses(this.fareRepository.findByFlightIdAndCabinClassId(flightId, cabinClassId));
    }

    @Override
    @Transactional
    public FareResponse updateFare(final UUID id, final FareRequest request) {
        final Fare fare = this.fareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fare", "id", id));

        final UUID flightId = request.getFlightId() != null ? request.getFlightId() : fare.getFlightId();
        final UUID cabinClassId =
                request.getCabinClassId() != null ? request.getCabinClassId() : fare.getCabinClassId();
        final String name = request.getName() != null ? request.getName() : fare.getName();

        if (this.fareRepository.existsByFlightIdAndCabinClassIdAndNameAndIdNot(
                flightId, cabinClassId, name, fare.getId())) {
            throw new BusinessException("Fare already exists with the same flight, cabin class, and name");
        }

        FareMapper.updateEntity(request, fare);
        final Fare updatedFare = this.fareRepository.save(fare);
        return this.toResponses(List.of(updatedFare)).get(0);
    }

    @Override
    @Transactional
    public void deleteFareById(final UUID id) {
        if (!this.fareRepository.existsById(id)) {
            throw new ResourceNotFoundException("Fare", "id", id);
        }
        // Không còn cascade/orphanRemoval sau khi gỡ @OneToOne -> tự xoá con trước, cha sau, trong 1 transaction
        this.baggagePolicyRepository.deleteByFareId(id);
        this.fareRuleRepository.deleteByFareId(id);
        this.fareRepository.deleteById(id);
    }

    @Override
    public Page<FareResponse> getFares(final Pageable pageable) {
        final Page<Fare> page = this.fareRepository.findAll(pageable);
        final List<FareResponse> responses = this.toResponses(page.getContent());
        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    @Override
    public Map<UUID, FareResponse> getLowestFarePerFlight(final List<UUID> flightIds, final UUID cabinClassId) {
        if (flightIds == null || flightIds.isEmpty()) {
            return Map.of();
        }

        final List<Fare> fares = this.fareRepository.findByFlightIdInAndCabinClassId(flightIds, cabinClassId);

        final Map<UUID, Fare> cheapestByFlightId = fares.stream()
                .collect(Collectors.toMap(
                        Fare::getFlightId, fare -> fare,
                        (existing, candidate) ->
                                candidate.getTotalPrice() < existing.getTotalPrice() ? candidate : existing));

        return this.toResponses(new ArrayList<>(cheapestByFlightId.values())).stream()
                .collect(Collectors.toMap(FareResponse::getFlightId, Function.identity()));
    }

    @Override
    public Map<UUID, FareResponse> getFareByIds(final List<UUID> ids) {
        final List<Fare> fares = this.fareRepository.findAllById(ids);
        return this.toResponses(fares).stream()
                .collect(Collectors.toMap(FareResponse::getId, Function.identity()));
    }

    /**
     * Công thức chống N+1 chuẩn: 1 query cha (đã có) + 1 query {@code IN} cho mỗi loại con,
     * ghép bằng Map trong bộ nhớ. Số query là hằng số (3), không phụ thuộc số dòng của {@code fares}.
     */
    private List<FareResponse> toResponses(final List<Fare> fares) {
        if (fares.isEmpty()) {
            return List.of();
        }

        final List<UUID> fareIds = fares.stream().map(Fare::getId).distinct().toList();

        final Map<UUID, FareRules> fareRulesByFareId = this.fareRuleRepository.findByFareIdIn(fareIds)
                .stream().collect(Collectors.toMap(FareRules::getFareId, Function.identity()));
        final Map<UUID, BaggagePolicy> baggagePolicyByFareId = this.baggagePolicyRepository.findByFareIdIn(fareIds)
                .stream().collect(Collectors.toMap(BaggagePolicy::getFareId, Function.identity()));

        final List<FareResponse> responses = new ArrayList<>(fares.size());
        for (final Fare fare : fares) {
            responses.add(FareMapper.toResponse(
                    fare, fareRulesByFareId.get(fare.getId()), baggagePolicyByFareId.get(fare.getId())));
        }
        return responses;
    }
}
