package com.nitditto.payload.response;

import com.nitditto.enums.CabinClassType;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FareResponse {

  private UUID id;
  private String name;
  private Character rbdCode;
  private UUID flightId;
  private UUID cabinClassId;
  private CabinClassType cabinClassType;

  // Pricing
  private Double baseFare;
  private Double taxesAndFees;
  private Double airlineFees;
  private Double currentPrice;
  private Double totalPrice;
  private String fareLabel;

  // Seat benefits
  private Boolean extraSeatSpace;
  private Boolean preferredSeatChoice;
  private Boolean advanceSeatSelection;
  private Boolean guaranteedSeatTogether;

  // Boarding benefits
  private Boolean priorityBoarding;
  private Boolean priorityCheckIn;
  private Boolean fastTrackSecurity;

  // In-flight benefits
  private Boolean complimentaryMeals;
  private Boolean premiumMealChoice;
  private Boolean inFlightInternet;
  private Boolean inFlightEntertainment;
  private Boolean complimentaryBeverages;

  // Flexibility benefits
  private Boolean freeDateChange;
  private Boolean partialRefund;
  private Boolean fullRefund;

  // Premium service benefits
  private Boolean loungeAccess;
  private Boolean airportTransfer;

  // Relationships
  private UUID fareRulesId;
  private FareRulesResponse fareRules;
  private  BaggagePolicyResponse baggagePolicy;

  // Audit
  private Instant createdAt;
  private Instant updatedAt;

}
