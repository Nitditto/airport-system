package com.nitditto.service;

import com.nitditto.payload.request.FareRulesRequest;
import com.nitditto.payload.response.FareRulesResponse;

import java.util.List;
import java.util.UUID;

public interface FareRulesService {
    FareRulesResponse createFareRules(FareRulesRequest fareRulesRequest) throws Exception;

    FareRulesResponse getFareRulesById(UUID id) throws Exception;

    FareRulesResponse getFareRulesByFareId(UUID fareId);

    List<FareRulesResponse> getFareRulesByAirlineId(UUID airlineId);

    FareRulesResponse updateFareRules(UUID id, FareRulesRequest fareRulesRequest) throws Exception;

    void deleteFareRules(UUID id);
}
