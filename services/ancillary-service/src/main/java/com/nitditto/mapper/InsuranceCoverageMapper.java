package com.nitditto.mapper;

import com.nitditto.model.Ancillary;
import com.nitditto.model.InsuranceCoverage;
import com.nitditto.payload.request.InsuranceCoverageRequest;
import com.nitditto.payload.response.InsuranceCoverageResponse;

public class InsuranceCoverageMapper {
    public static InsuranceCoverage toEntity(InsuranceCoverageRequest request) {
        if (request == null)
            return null;
        return InsuranceCoverage.builder()
                .ancillaryId(request.getAncillaryId())
                .coverageType(request.getCoverageType())
                .name(request.getName())
                .description(request.getDescription())
                .coverageAmount(request.getCoverageAmount())
                .emergencyContact(request.getEmergencyContact())
                .isFlat(request.getIsFlat() != null ? request.getIsFlat() : true)
                .claimCondition(request.getClaimCondition())
                .displayOrder(request.getDisplayOrder())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
    }

    public static InsuranceCoverageResponse toResponse(InsuranceCoverage insuranceCoverage, Ancillary ancillary) {
        if (insuranceCoverage == null)
            return null;
        return InsuranceCoverageResponse.builder()
                .id(insuranceCoverage.getId())
                .ancillaryId(insuranceCoverage.getAncillaryId())
                .ancillaryName(ancillary != null ? ancillary.getName() : null)
                .coverageType(insuranceCoverage.getCoverageType())
                .name(insuranceCoverage.getName())
                .description(insuranceCoverage.getDescription())
                .coverageAmount(insuranceCoverage.getCoverageAmount())
                .emergencyContact(insuranceCoverage.getEmergencyContact())
                .isFlat(insuranceCoverage.isFlat())
                .claimCondition(insuranceCoverage.getClaimCondition())
                .displayOrder(insuranceCoverage.getDisplayOrder())
                .active(insuranceCoverage.isActive())
                .build();
    }

    public static void updateEntityFromRequest(InsuranceCoverage insuranceCoverage, InsuranceCoverageRequest request, Ancillary ancillary) {
        if (insuranceCoverage == null || request == null) return;
        if (ancillary != null) insuranceCoverage.setAncillaryId(request.getAncillaryId());
        if (request.getCoverageType() != null) insuranceCoverage.setCoverageType(request.getCoverageType());
        if (request.getName() != null) insuranceCoverage.setName(request.getName());
        if (request.getDescription() != null) insuranceCoverage.setDescription(request.getDescription());
        if (request.getCoverageAmount() != null) insuranceCoverage.setCoverageAmount(request.getCoverageAmount());
        if (request.getIsFlat() != null) insuranceCoverage.setFlat(request.getIsFlat());
        if (request.getClaimCondition() != null) insuranceCoverage.setClaimCondition(request.getClaimCondition());
        if (request.getEmergencyContact() != null) insuranceCoverage.setEmergencyContact(request.getEmergencyContact());
        if (request.getDisplayOrder() != null) insuranceCoverage.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) insuranceCoverage.setActive(request.getActive());
    }
}
