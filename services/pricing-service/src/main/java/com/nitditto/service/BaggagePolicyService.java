package com.nitditto.service;

import com.nitditto.payload.request.BaggagePolicyRequest;
import com.nitditto.payload.response.BaggagePolicyResponse;

import java.util.List;

public interface BaggagePolicyService {

    BaggagePolicyResponse createBaggagePolicy(BaggagePolicyRequest baggagePolicyRequest) throws Exception;

    BaggagePolicyResponse getBaggagePolicyById(Long id) throws Exception;

    BaggagePolicyResponse getBaggagePolicyByFareId(Long fareId);

    List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(Long airlineId);

    BaggagePolicyResponse updateBaggagePolicy(Long id, BaggagePolicyRequest baggagePolicyRequest) throws Exception;

    void deleteBaggagePolicy(Long id);
}
