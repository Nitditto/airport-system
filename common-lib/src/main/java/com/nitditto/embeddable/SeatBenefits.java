package com.nitditto.embeddable;

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
public class SeatBenefits {

  private Boolean extraSeatSpace = false;
  private Boolean preferredSeatChoice = false;
  private Boolean advanceSetSelection = false;
  private Boolean guaranteedSeatTogether = false;
}
