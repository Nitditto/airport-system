package com.nitditto.service.integration;

import java.util.UUID;

import com.nitditto.client.PricingClient;
import com.nitditto.payload.response.FareResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FareIntegrationService {

  private final PricingClient pricingClient;

  public Double calculateFareTotal(UUID fareId) {
    FareResponse fareResponse = pricingClient.getFareById(fareId).getData();
    Double baseFare = fareResponse.getBaseFare();
    Double taxesAndFeess = fareResponse.getTaxesAndFees() != null ? fareResponse.getTaxesAndFees() : 0;
    Double airlineFees = fareResponse.getAirlineFees() != null ? fareResponse.getAirlineFees() : 0;

    return baseFare + taxesAndFeess + airlineFees;
  }
}
