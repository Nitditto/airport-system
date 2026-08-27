package com.nitditto.service.impl;

import com.nitditto.mapper.BaggagePolicyMapper;
import com.nitditto.model.BaggagePolicy;
import com.nitditto.payload.request.BaggagePolicyRequest;
import com.nitditto.payload.response.BaggagePolicyResponse;
import com.nitditto.repository.BaggagePolicyRepository;
import com.nitditto.repository.FareRepository;
import com.nitditto.service.BaggagePolicyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaggagePolicyServiceImpl implements BaggagePolicyService {

    private final BaggagePolicyRepository baggagePolicyRepository;
    private final FareRepository fareRepository;

    @Override
    public BaggagePolicyResponse createBaggagePolicy(final BaggagePolicyRequest baggagePolicyRequest) throws Exception {
        if (!this.fareRepository.existsById(baggagePolicyRequest.getFareId())) {
            throw new Exception("Fare not found");
        }

        if (this.baggagePolicyRepository.existsByFareId(baggagePolicyRequest.getFareId())) {
            throw new Exception("Baggage policy already exists");
        }
        final BaggagePolicy baggagePolicy =
                BaggagePolicyMapper.toEntity(baggagePolicyRequest, baggagePolicyRequest.getFareId());
        final BaggagePolicy saved = this.baggagePolicyRepository.save(baggagePolicy);

        return BaggagePolicyMapper.toResponse(saved);
    }

    @Override
    public BaggagePolicyResponse getBaggagePolicyById(final UUID id) throws Exception {
        final BaggagePolicy baggagePolicy = this.baggagePolicyRepository.findById(id).orElseThrow(
                () -> new Exception("Policy not found with id")
        );

        return BaggagePolicyMapper.toResponse(baggagePolicy);
    }

    @Override
    public BaggagePolicyResponse getBaggagePolicyByFareId(final UUID fareId) {
        final BaggagePolicy baggagePolicy = this.baggagePolicyRepository.findByFareId(fareId);
        return BaggagePolicyMapper.toResponse(baggagePolicy);
    }

    @Override
    public List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(final UUID airlineId) {
        return this.baggagePolicyRepository.findByAirlineId(airlineId).stream().map(
                BaggagePolicyMapper::toResponse
        ).collect(Collectors.toList());
    }

    @Override
    public BaggagePolicyResponse updateBaggagePolicy(final UUID id, final BaggagePolicyRequest baggagePolicyRequest) throws Exception {
        final BaggagePolicy baggagePolicy = this.baggagePolicyRepository.findById(id).orElseThrow(
                () -> new Exception("Policy not found with id")
        );
        BaggagePolicyMapper.updateEntity(baggagePolicyRequest, baggagePolicy);
        final BaggagePolicy saved = this.baggagePolicyRepository.save(baggagePolicy);
        return BaggagePolicyMapper.toResponse(saved);
    }

    @Override
    public void deleteBaggagePolicy(final UUID id) {
        final BaggagePolicy baggagePolicy = this.baggagePolicyRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException("Baggage policy not found with id: " + id)
        );
        this.baggagePolicyRepository.delete(baggagePolicy);
    }
}
