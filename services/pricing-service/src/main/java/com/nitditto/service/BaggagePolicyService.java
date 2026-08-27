package com.nitditto.service;

import com.nitditto.payload.request.BaggagePolicyRequest;
import com.nitditto.payload.response.BaggagePolicyResponse;

import java.util.List;
import java.util.UUID;

public interface BaggagePolicyService {

    BaggagePolicyResponse createBaggagePolicy(BaggagePolicyRequest baggagePolicyRequest);

    BaggagePolicyResponse getBaggagePolicyById(UUID id);

    BaggagePolicyResponse getBaggagePolicyByFareId(UUID fareId);

    List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(UUID airlineId);

    BaggagePolicyResponse updateBaggagePolicy(UUID id, BaggagePolicyRequest baggagePolicyRequest);

    void deleteBaggagePolicy(UUID id);
}
