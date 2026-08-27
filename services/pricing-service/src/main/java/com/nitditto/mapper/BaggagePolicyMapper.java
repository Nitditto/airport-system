package com.nitditto.mapper;

import com.nitditto.model.BaggagePolicy;
import com.nitditto.payload.request.BaggagePolicyRequest;
import com.nitditto.payload.response.BaggagePolicyResponse;
import java.util.UUID;

public class BaggagePolicyMapper {

    public static BaggagePolicy toEntity(final BaggagePolicyRequest request, final UUID fareId) {
        if (request == null)
            return null;

        return BaggagePolicy.builder()
                .fareId(fareId)
                .name(request.getName())
                .description(request.getDescription())
                .cabinBaggageMaxWeight(request.getCabinBaggageMaxWeight())
                .cabinBaggagePieces(request.getCabinBaggagePieces() != null ? request.getCabinBaggagePieces() : 0)
                .cabinBaggageWeightPerPiece(request.getCabinBaggageWeightPerPiece())
                .cabinBaggageMaxDimension(request.getCabinBaggageMaxDimension())
                .checkInBaggageMaxWeight(request.getCheckInBaggageMaxWeight())
                .checkInBaggagePieces(request.getCheckInBaggagePieces() != null ? request.getCheckInBaggagePieces() : 0)
                .checkInBaggageWeightPerPiece(request.getCheckInBaggageWeightPerPiece())
                .freeCheckedBagsAllowance(request.getFreeCheckedBagsAllowance() != null ? request.getFreeCheckedBagsAllowance() : 0)
                .priorityBaggage(request.getPriorityBaggage() != null ? request.getPriorityBaggage() : false)
                .extraBaggageAllowance(request.getExtraBaggageAllowance() != null ? request.getExtraBaggageAllowance() : false)
                .build();
    }

    public static BaggagePolicyResponse toResponse(final BaggagePolicy baggagePolicy) {
        if (baggagePolicy == null)
            return null;

        return BaggagePolicyResponse.builder()
                .id(baggagePolicy.getId())
                .name(baggagePolicy.getName())
                .description(baggagePolicy.getDescription())
                .cabinBaggageMaxWeight(baggagePolicy.getCabinBaggageMaxWeight())
                .cabinBaggagePieces(baggagePolicy.getCabinBaggagePieces())
                .cabinBaggageWeightPerPiece(baggagePolicy.getCabinBaggageWeightPerPiece())
                .cabinBaggageMaxDimension(baggagePolicy.getCabinBaggageMaxDimension())
                .checkInBaggageMaxWeight(baggagePolicy.getCheckInBaggageMaxWeight())
                .checkInBaggagePieces(baggagePolicy.getCheckInBaggagePieces())
                .checkInBaggageWeightPerPiece(baggagePolicy.getCheckInBaggageWeightPerPiece())
                .freeCheckedBagsAllowance(baggagePolicy.getFreeCheckedBagsAllowance())
                .priorityBaggage(baggagePolicy.getPriorityBaggage())
                .extraBaggageAllowance(baggagePolicy.getExtraBaggageAllowance())
                .airlineId(baggagePolicy.getAirlineId())
                .fareId(baggagePolicy.getFareId())
                .createdAt(baggagePolicy.getCreatedAt())
                .updatedAt(baggagePolicy.getUpdatedAt())
                .build();
    }

    public static void updateEntity(BaggagePolicyRequest request, BaggagePolicy existing) {
        if (request == null || existing == null)
            return;
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getCabinBaggageMaxWeight() != null)
            existing.setCabinBaggageMaxWeight(request.getCabinBaggageMaxWeight());
        if (request.getCabinBaggagePieces() != null) existing.setCheckInBaggagePieces(request.getCabinBaggagePieces());
        if (request.getCabinBaggageWeightPerPiece() != null)
            existing.setCabinBaggageWeightPerPiece(request.getCabinBaggageWeightPerPiece());
        if (request.getCabinBaggageMaxDimension() != null)
            existing.setCabinBaggageMaxDimension(request.getCabinBaggageMaxDimension());
        if (request.getCheckInBaggageMaxWeight() != null)
            existing.setCheckInBaggageMaxWeight(request.getCheckInBaggageMaxWeight());
        if (request.getCheckInBaggagePieces() != null)
            existing.setCheckInBaggagePieces(request.getCheckInBaggagePieces());
        if (request.getCheckInBaggageWeightPerPiece() != null)
            existing.setCheckInBaggageWeightPerPiece(request.getCheckInBaggageWeightPerPiece());
        if (request.getFreeCheckedBagsAllowance() != null)
            existing.setFreeCheckedBagsAllowance(request.getFreeCheckedBagsAllowance());
        if (request.getPriorityBaggage() != null) existing.setPriorityBaggage(request.getPriorityBaggage());
        if (request.getExtraBaggageAllowance() != null)
            existing.setExtraBaggageAllowance(request.getExtraBaggageAllowance());
    }
}
