package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FareRuleMapper;
import com.nitditto.model.FareRules;
import com.nitditto.payload.request.FareRulesRequest;
import com.nitditto.payload.response.FareRulesResponse;
import com.nitditto.repository.FareRepository;
import com.nitditto.repository.FareRuleRepository;
import com.nitditto.service.FareRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FareRulesServiceImpl implements FareRulesService {

    private final FareRuleRepository fareRuleRepository;
    private final FareRepository fareRepository;

    @Override
    public FareRulesResponse createFareRules(final FareRulesRequest fareRulesRequest) {
        if (!this.fareRepository.existsById(fareRulesRequest.getFareId())) {
            throw new ResourceNotFoundException("Fare", "id", fareRulesRequest.getFareId());
        }

        if (this.fareRuleRepository.existsByFareId(fareRulesRequest.getFareId())) {
            throw new BusinessException("Fare rules already exist for this fare");
        }

        final FareRules fareRules = FareRuleMapper.toEntity(fareRulesRequest, fareRulesRequest.getFareId());
        final FareRules savedFareRules = this.fareRuleRepository.save(fareRules);
        return FareRuleMapper.toResponse(savedFareRules);
    }

    @Override
    public FareRulesResponse getFareRulesById(final UUID id) {
        final FareRules fareRules = this.fareRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FareRules", "id", id));

        return FareRuleMapper.toResponse(fareRules);
    }

    @Override
    public FareRulesResponse getFareRulesByFareId(final UUID fareId) {
        final FareRules fareRules = this.fareRuleRepository.findByFareId(fareId);

        return FareRuleMapper.toResponse(fareRules);
    }

    @Override
    public List<FareRulesResponse> getFareRulesByAirlineId(final UUID airlineId) {
        return this.fareRuleRepository.findByAirlineId(airlineId).stream()
                .map(FareRuleMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FareRulesResponse updateFareRules(final UUID id, final FareRulesRequest fareRulesRequest) {
        final FareRules fareRules = this.fareRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FareRules", "id", id));
        FareRuleMapper.updateEntity(fareRulesRequest, fareRules);
        final FareRules savedFareRules = this.fareRuleRepository.save(fareRules);

        return FareRuleMapper.toResponse(savedFareRules);
    }

    @Override
    public void deleteFareRules(final UUID id) {
        final FareRules fareRules = this.fareRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FareRules", "id", id));
        this.fareRuleRepository.delete(fareRules);
    }
}
