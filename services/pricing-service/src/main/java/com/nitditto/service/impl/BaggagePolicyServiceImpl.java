package com.nitditto.service.impl;

import com.nitditto.mapper.BaggagePolicyMapper;
import com.nitditto.model.BaggagePolicy;
import com.nitditto.model.Fare;
import com.nitditto.payload.request.BaggagePolicyRequest;
import com.nitditto.payload.response.BaggagePolicyResponse;
import com.nitditto.repository.BaggagePolicyRepository;
import com.nitditto.repository.FareRepository;
import com.nitditto.service.BaggagePolicyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaggagePolicyServiceImpl implements BaggagePolicyService {

    private final BaggagePolicyRepository baggagePolicyRepository;
    private final FareRepository fareRepository;

    @Override
    public BaggagePolicyResponse createBaggagePolicy(BaggagePolicyRequest baggagePolicyRequest) throws Exception {
        Fare fare = fareRepository.findById(baggagePolicyRequest.getFareId()).orElseThrow(
                () -> new Exception("Fare not found")
        );

        if (baggagePolicyRepository.existsByFareId(fare.getId())) {
            throw new Exception("Baggage policy already exists");
        }
        BaggagePolicy baggagePolicy = BaggagePolicyMapper.toEntity(baggagePolicyRequest, fare);
        BaggagePolicy saved = baggagePolicyRepository.save(baggagePolicy);

        return BaggagePolicyMapper.toResponse(saved);
    }

    @Override
    public BaggagePolicyResponse getBaggagePolicyById(Long id) throws Exception {
        BaggagePolicy baggagePolicy = baggagePolicyRepository.findById(id).orElseThrow(
                () -> new Exception("Policy not found with id")
        );

        return BaggagePolicyMapper.toResponse(baggagePolicy);
    }

    @Override
    public BaggagePolicyResponse getBaggagePolicyByFareId(Long fareId) {
        BaggagePolicy baggagePolicy = baggagePolicyRepository.findByFareId(fareId);
        return BaggagePolicyMapper.toResponse(baggagePolicy);
    }

    @Override
    public List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(Long airlineId) {
        return baggagePolicyRepository.findByAirlineId(airlineId).stream().map(
                BaggagePolicyMapper::toResponse
        ).collect(Collectors.toList());
    }

    @Override
    public BaggagePolicyResponse updateBaggagePolicy(Long id, BaggagePolicyRequest baggagePolicyRequest) throws Exception {
        BaggagePolicy baggagePolicy = baggagePolicyRepository.findById(id).orElseThrow(
                () -> new Exception("Policy not found with id")
        );
        BaggagePolicyMapper.updateEntity(baggagePolicyRequest, baggagePolicy);
        BaggagePolicy saved = baggagePolicyRepository.save(baggagePolicy);
        return BaggagePolicyMapper.toResponse(saved);
    }

    @Override
    public void deleteBaggagePolicy(Long id) {
        BaggagePolicy baggagePolicy = baggagePolicyRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException("Baggage policy not found with id: " + id)
        );
        baggagePolicyRepository.delete(baggagePolicy);
    }
}
