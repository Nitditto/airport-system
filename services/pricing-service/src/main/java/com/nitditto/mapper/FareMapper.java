package com.nitditto.mapper;

import com.nitditto.embeddable.BoardingBenefits;
import com.nitditto.embeddable.FlexibilityBenefits;
import com.nitditto.embeddable.InFlightBenefits;
import com.nitditto.embeddable.PremiumServiceBenefits;
import com.nitditto.embeddable.SeatBenefits;
import com.nitditto.model.Fare;
import com.nitditto.payload.request.FareRequest;
import com.nitditto.payload.response.FareResponse;

public class FareMapper {

    public static Fare toEntity(FareRequest request) {
        if (request == null) {
            return null;
        }

        Double calculatedPrice = request.getCurrentPrice();
        if (calculatedPrice == null) {
            calculatedPrice =
                    request.getBaseFare() + request.getTaxesAndFees() + request.getAirlineFees();
        }

        SeatBenefits seatBenefits = SeatBenefits.builder()
                .extraSeatSpace(bool(request.getExtraSeatSpace()))
                .preferredSeatChoice(bool(request.getPreferredSeatChoice()))
                .advanceSetSelection(bool(request.getAdvanceSeatSelection()))
                .guaranteedSeatTogether(bool(request.getGuaranteedSeatTogether()))
                .build();

        BoardingBenefits boardingBenefits = BoardingBenefits.builder()
                .priorityBoarding(bool(request.getPriorityBoarding()))
                .priorityCheckin((bool(request.getPriorityCheckin())))
                .fastTrackSecurity(bool(request.getFastTrackSecurity()))
                .build();

        InFlightBenefits inFlightBenefits = InFlightBenefits.builder()
                .complimentaryMeals(bool(request.getComplimentaryMeals()))
                .complimentaryBeverages(bool(request.getComplimentaryBeverages()))
                .inFlightInternet(bool(request.getInFlightInternet()))
                .inFlightEntertainment(bool(request.getInFlightEntertainment()))
                .complimentaryBeverages(bool(request.getComplimentaryBeverages()))
                .build();

        FlexibilityBenefits flexibilityBenefits = FlexibilityBenefits.builder()
                .freeDateChange(bool(request.getFreeDateChange()))
                .partialRefund(bool(request.getPartialRefund()))
                .fullRefund(bool(request.getFullRefund()))
                .build();

        PremiumServiceBenefits premiumServiceBenefits = PremiumServiceBenefits.builder()
                .loungeAccess(bool(request.getLoungeAccess()))
                .airportTransfer(bool(request.getAirportTransfer()))
                .build();

        return Fare.builder()
                .name(request.getName())
                .rbdCode(request.getRbdCode())
                .flightId(request.getFlightId())
                .cabinClassId(request.getCabinClassId())
                .baseFare(request.getBaseFare())
                .taxedAndFees(request.getTaxesAndFees())
                .airlineFees(request.getAirlineFees())
                .currentPrice(calculatedPrice)
                .fareLabel(request.getFareLabel())
                .seatBenefits(seatBenefits)
                .boardingBenefits(boardingBenefits)
                .inFlightBenefits(inFlightBenefits)
                .flexibilityBenefits(flexibilityBenefits)
                .premiumServiceBenefits(premiumServiceBenefits)
                .build();
    }

    public static FareResponse toResponse(Fare fare) {
        if (fare == null) {
            return null;
        }

        return FareResponse.builder()
                .id(fare.getId())
                .name(fare.getName())
                .rbdCode(fare.getRbdCode())
                .flightId(fare.getFlightId())
                .cabinClassId(fare.getCabinClassId())
                .baseFare(fare.getBaseFare())
                .taxesAndFees(fare.getTaxedAndFees())
                .airlineFees(fare.getAirlineFees())
                .currentPrice(fare.getCurrentPrice())
                .totalPrice(fare.getTotalPrice())
                .fareLabel(fare.getFareLabel())
                .fareRulesId(fare.getFareRules() != null ? fare.getFareRules().getId() : null)

                // Seat benefits
                .extraSeatSpace(
                        fare.getSeatBenefits() != null ? fare.getSeatBenefits().getExtraSeatSpace() : false)
                .preferredSeatChoice(
                        fare.getSeatBenefits() != null ? fare.getSeatBenefits().getPreferredSeatChoice()
                                : false)
                .advanceSeatSelection(
                        fare.getSeatBenefits() != null ? fare.getSeatBenefits().getAdvanceSetSelection()
                                : false)
                .guaranteedSeatTogether(
                        fare.getSeatBenefits() != null ? fare.getSeatBenefits().getGuaranteedSeatTogether()
                                : false)

                // Boarding benefits
                .priorityBoarding(
                        fare.getBoardingBenefits() != null ? fare.getBoardingBenefits().getPriorityBoarding()
                                : false)
                .priorityCheckIn(
                        fare.getBoardingBenefits() != null ? fare.getBoardingBenefits().getPriorityCheckin()
                                : false)
                .fastTrackSecurity(
                        fare.getBoardingBenefits() != null ? fare.getBoardingBenefits().getFastTrackSecurity()
                                : false)

                // In-flight benefits
                .complimentaryMeals(
                        fare.getInFlightBenefits() != null ? fare.getInFlightBenefits().getComplimentaryMeals()
                                : false)
                .premiumMealChoice(
                        fare.getInFlightBenefits() != null ? fare.getInFlightBenefits().getPremiumMealChoice()
                                : false)
                .inFlightInternet(
                        fare.getInFlightBenefits() != null ? fare.getInFlightBenefits().getInFlightInternet()
                                : false)
                .inFlightEntertainment(fare.getInFlightBenefits() != null ? fare.getInFlightBenefits()
                        .getInFlightEntertainment() : false)
                .complimentaryBeverages(fare.getInFlightBenefits() != null ? fare.getInFlightBenefits()
                        .getComplimentaryBeverages() : false)

                // Flexibility benefits
                .freeDateChange(fare.getFlexibilityBenefits() != null ? fare.getFlexibilityBenefits()
                        .getFreeDateChange() : false)
                .partialRefund(
                        fare.getFlexibilityBenefits() != null ? fare.getFlexibilityBenefits().getPartialRefund()
                                : false)
                .fullRefund(
                        fare.getFlexibilityBenefits() != null ? fare.getFlexibilityBenefits().getFullRefund()
                                : false)

                // Premium service benefits
                .loungeAccess(fare.getPremiumServiceBenefits() != null ?
                        fare.getPremiumServiceBenefits().getLoungeAccess() : false)
                .airportTransfer(fare.getPremiumServiceBenefits() != null ? fare.getPremiumServiceBenefits()
                        .getAirportTransfer() : false)

                // Nested responses
                .fareRules(fare.getFareRules() != null ? FareRuleMapper.toResponse(fare.getFareRules()) : null)
                .baggagePolicy(fare.getBaggagePolicy() != null ? BaggagePolicyMapper.toResponse(fare.getBaggagePolicy()) : null)
                .createdAt(fare.getCreatedAt())
                .updatedAt(fare.getUpdatedAt())
                .build();
    }

    public static void updateEntity(FareRequest fareRequest, Fare existingFare) {
        if (fareRequest == null || existingFare == null) {
            return;
        }

        if (fareRequest.getName() != null) {
            existingFare.setName(fareRequest.getName());
        }
        if (fareRequest.getRbdCode() != null) {
            existingFare.setRbdCode(fareRequest.getRbdCode());
        }
        if (fareRequest.getFlightId() != null) {
            existingFare.setFlightId(fareRequest.getFlightId());
        }
        if (fareRequest.getCabinClassId() != null) {
            existingFare.setCabinClassId(fareRequest.getCabinClassId());
        }

        if (fareRequest.getBaseFare() != null) {
            existingFare.setBaseFare(fareRequest.getBaseFare());
        }
        if (fareRequest.getTaxesAndFees() != null) {
            existingFare.setTaxedAndFees(fareRequest.getTaxesAndFees());
        }
        if (fareRequest.getAirlineFees() != null) {
            existingFare.setAirlineFees(fareRequest.getAirlineFees());
        }
        if (fareRequest.getCurrentPrice() != null) {
            existingFare.setCurrentPrice(fareRequest.getCurrentPrice());
        }
        if (fareRequest.getFareLabel() != null) {
            existingFare.setFareLabel(fareRequest.getFareLabel());
        }

        // Update embedded benefits
        SeatBenefits seatBenefits = existingFare.getSeatBenefits();
        if (fareRequest.getExtraSeatSpace() != null) {
            seatBenefits.setExtraSeatSpace(fareRequest.getExtraSeatSpace());
        }
        if (fareRequest.getPreferredSeatChoice() != null) {
            seatBenefits.setPreferredSeatChoice(fareRequest.getPreferredSeatChoice());
        }
        if (fareRequest.getAdvanceSeatSelection() != null) {
            seatBenefits.setAdvanceSetSelection(fareRequest.getAdvanceSeatSelection());
        }
        if (fareRequest.getGuaranteedSeatTogether() != null) {
            seatBenefits.setGuaranteedSeatTogether(fareRequest.getGuaranteedSeatTogether());
        }

        BoardingBenefits boardingBenefits = existingFare.getBoardingBenefits();
        if (fareRequest.getPriorityBoarding() != null) {
            boardingBenefits.setPriorityBoarding(fareRequest.getPriorityBoarding());
        }
        if (fareRequest.getPriorityCheckin() != null) {
            boardingBenefits.setPriorityCheckin(fareRequest.getPriorityCheckin());
        }
        if (fareRequest.getFastTrackSecurity() != null) {
            boardingBenefits.setFastTrackSecurity(fareRequest.getFastTrackSecurity());
        }

        InFlightBenefits inFlightBenefits = existingFare.getInFlightBenefits();
        if (fareRequest.getComplimentaryMeals() != null) {
            inFlightBenefits.setComplimentaryMeals(fareRequest.getComplimentaryMeals());
        }
        if (fareRequest.getPremiumMealChoice() != null) {
            inFlightBenefits.setPremiumMealChoice(fareRequest.getPremiumMealChoice());
        }
        if (fareRequest.getInFlightInternet() != null) {
            inFlightBenefits.setInFlightInternet(fareRequest.getInFlightInternet());
        }
        if (fareRequest.getInFlightEntertainment() != null) {
            inFlightBenefits.setInFlightEntertainment(fareRequest.getInFlightEntertainment());
        }
        if (fareRequest.getComplimentaryBeverages() != null) {
            inFlightBenefits.setComplimentaryBeverages(fareRequest.getComplimentaryBeverages());
        }

        FlexibilityBenefits flexibilityBenefits = existingFare.getFlexibilityBenefits();
        if (fareRequest.getFreeDateChange() != null) {
            flexibilityBenefits.setFreeDateChange(fareRequest.getFreeDateChange());
        }
        if (fareRequest.getPartialRefund() != null) {
            flexibilityBenefits.setPartialRefund(fareRequest.getPartialRefund());
        }
        if (fareRequest.getFullRefund() != null) {
            flexibilityBenefits.setFullRefund(fareRequest.getFullRefund());
        }

        PremiumServiceBenefits premiumServiceBenefits = existingFare.getPremiumServiceBenefits();
        if (fareRequest.getLoungeAccess() != null) {
            premiumServiceBenefits.setLoungeAccess(fareRequest.getLoungeAccess());
        }
        if (fareRequest.getAirportTransfer() != null) {
            premiumServiceBenefits.setAirportTransfer(fareRequest.getAirportTransfer());
        }

    }

    private static boolean bool(Boolean value) {
        return value != null && value;
    }
}
