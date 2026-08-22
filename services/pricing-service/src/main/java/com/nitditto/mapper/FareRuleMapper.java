package com.nitditto.mapper;

import com.nitditto.model.Fare;
import com.nitditto.model.FareRules;
import com.nitditto.payload.request.FareRulesRequest;
import com.nitditto.payload.response.FareRulesResponse;

public class FareRuleMapper {

    public static FareRules toEntity(FareRulesRequest fareRulesRequest, Fare fare) {
        if (fareRulesRequest == null) {
            return null;
        }

        return FareRules.builder()
                .ruleName(fareRulesRequest.getRuleName())
                .fare(fare)
                .airlineId(fareRulesRequest.getAirlineId())
                .isRefundable(fareRulesRequest.getIsRefundable())
                .changeFee(fareRulesRequest.getChangeFee())
                .cancellationFee(fareRulesRequest.getCancellationFee())
                .refundDeadlineDays(fareRulesRequest.getRefundDeadlineDays())
                .changeDeadlineHours(fareRulesRequest.getChangeDeadlineHours())
                .isChangeable(fareRulesRequest.getIsChangeable() != null ? fareRulesRequest.getIsChangeable() : false)
                .build();
    }

    public static FareRulesResponse toResponse(FareRules fareRules) {
        if (fareRules == null) {
            return null;
        }

        return FareRulesResponse.builder()
                .id(fareRules.getId())
                .ruleName(fareRules.getRuleName())
                .fareId(fareRules.getFare() != null ? fareRules.getFare().getId() : null)
                .airlineId(fareRules.getAirlineId())
                .isRefundable(fareRules.getIsRefundable())
                .changeFee(fareRules.getChangeFee())
                .cancellationFee(fareRules.getCancellationFee())
                .refundDeadlineDays(fareRules.getRefundDeadlineDays())
                .changeDeadlineHours(fareRules.getChangeDeadlineHours())
                .isChangeable(fareRules.getIsChangeable())
                .createdAt(fareRules.getCreatedAt())
                .updatedAt(fareRules.getUpdatedAt())
                .build();
    }

    public static void updateEntity(FareRulesRequest fareRulesRequest, FareRules existingFareRules) {

        if(fareRulesRequest == null || existingFareRules == null) {
            return;
        }
        if(fareRulesRequest.getRuleName() != null) {
            existingFareRules.setRuleName(fareRulesRequest.getRuleName());
        }
        if(fareRulesRequest.getAirlineId() != null) {
            existingFareRules.setAirlineId(fareRulesRequest.getAirlineId());
        }
        if(fareRulesRequest.getIsRefundable() != null) {
            existingFareRules.setIsRefundable(fareRulesRequest.getIsRefundable());
        }
        if(fareRulesRequest.getChangeFee() != null) {
            existingFareRules.setChangeFee(fareRulesRequest.getChangeFee());
        }
        if(fareRulesRequest.getCancellationFee() != null) {
            existingFareRules.setCancellationFee(fareRulesRequest.getCancellationFee());
        }
        if(fareRulesRequest.getRefundDeadlineDays() != null) {
            existingFareRules.setRefundDeadlineDays(fareRulesRequest.getRefundDeadlineDays());
        }
        if(fareRulesRequest.getChangeDeadlineHours() != null) {
            existingFareRules.setChangeDeadlineHours(fareRulesRequest.getChangeDeadlineHours());
        }
        if(fareRulesRequest.getIsChangeable() != null) {
            existingFareRules.setIsChangeable(fareRulesRequest.getIsChangeable());
        }
    }
}
