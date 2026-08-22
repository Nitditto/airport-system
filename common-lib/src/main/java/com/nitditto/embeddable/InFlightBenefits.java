package com.nitditto.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InFlightBenefits {

  @Column(name = "complimentary_meals", nullable = false)
  @Builder.Default
  private Boolean complimentaryMeals = false;

  @Column(name = "premium_meal_choice", nullable = false)
  @Builder.Default
  private Boolean premiumMealChoice = false;

  @Column(name = "in_flight_internet", nullable = false)
  @Builder.Default
  private Boolean inFlightInternet = false;

  @Column(name = "in_flight_entertainment", nullable = false)
  @Builder.Default
  private Boolean inFlightEntertainment = false;

  @Column(name = "complimentary_beverages", nullable = false)
  @Builder.Default
  private Boolean complimentaryBeverages = false;
}
