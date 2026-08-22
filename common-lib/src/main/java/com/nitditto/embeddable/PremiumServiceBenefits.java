package com.nitditto.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumServiceBenefits {

  @Column(name = "lounge_access", nullable = false)
  @Builder.Default
  private Boolean loungeAccess = false;

  @Column(name = "airport_transfer", nullable = false)
  @Builder.Default
  private Boolean airportTransfer = false;
}
