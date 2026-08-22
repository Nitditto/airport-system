package com.nitditto.service;

import com.nitditto.payload.request.FareRulesRequest;
import com.nitditto.payload.response.FareRulesResponse;

import java.util.List;

public interface FareRulesService {
    FareRulesResponse createFareRules(FareRulesRequest fareRulesRequest) throws Exception;

    FareRulesResponse getFareRulesById(Long id) throws Exception;

    FareRulesResponse getFareRulesByFareId(Long fareId);

    List<FareRulesResponse> getFareRulesByAirlineId(Long airlineId);

    FareRulesResponse updateFareRules(Long id, FareRulesRequest fareRulesRequest) throws Exception;

    void deleteFareRules(Long id);
}
