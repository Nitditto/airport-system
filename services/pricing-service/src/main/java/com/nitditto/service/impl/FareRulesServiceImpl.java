package com.nitditto.service.impl;

import com.nitditto.mapper.FareRuleMapper;
import com.nitditto.model.Fare;
import com.nitditto.model.FareRules;
import com.nitditto.payload.request.FareRulesRequest;
import com.nitditto.payload.response.FareRulesResponse;
import com.nitditto.repository.FareRepository;
import com.nitditto.repository.FareRuleRepository;
import com.nitditto.service.FareRulesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FareRulesServiceImpl implements FareRulesService {

    private final FareRuleRepository fareRuleRepository;
    private final FareRepository fareRepository;

    @Override
    public FareRulesResponse createFareRules(FareRulesRequest fareRulesRequest) throws Exception {
        Fare fare = fareRepository.findById(fareRulesRequest.getFareId()).orElseThrow(
                () -> new Exception("Fare not found")
        );

        if (fareRuleRepository.existsByFareId(fare.getId())) {
            throw new Exception("Fare rules already exist for this fare");
        }

        FareRules fareRules = FareRuleMapper.toEntity(fareRulesRequest, fare);
        FareRules savedFareRules = fareRuleRepository.save(fareRules);
        return FareRuleMapper.toResponse(savedFareRules);
    }

    @Override
    public FareRulesResponse getFareRulesById(Long id) throws Exception {
        FareRules fareRules = fareRuleRepository.findById(id).orElseThrow(
                () -> new Exception("Fare rules not found")
        );

        return FareRuleMapper.toResponse(fareRules);
    }

    @Override
    public FareRulesResponse getFareRulesByFareId(Long fareId) {
        FareRules fareRules = fareRuleRepository.findByFareId(fareId);

        return FareRuleMapper.toResponse(fareRules);
    }

    @Override
    public List<FareRulesResponse> getFareRulesByAirlineId(Long airlineId) {
        return fareRuleRepository.findByAirlineId(airlineId).stream()
                .map(FareRuleMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FareRulesResponse updateFareRules(Long id, FareRulesRequest fareRulesRequest) throws Exception {
        FareRules fareRules = fareRuleRepository.findById(id).orElseThrow(
                () -> new Exception("Fare rules not found")
        );
        FareRuleMapper.updateEntity(fareRulesRequest, fareRules);
        FareRules savedFareRules = fareRuleRepository.save(fareRules);

        return FareRuleMapper.toResponse(savedFareRules);
    }

    @Override
    public void deleteFareRules(Long id) {
        FareRules fareRules = fareRuleRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Fare rules not found with id:" + id));
        fareRuleRepository.delete(fareRules);
    }
}
