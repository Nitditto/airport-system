package com.nitditto.service.integration;

import com.nitditto.client.PricingClient;
import com.nitditto.payload.response.FareResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FareIntegrationService {

  private final PricingClient pricingClient;

  /** Giá vé của MỘT hành khách (base + thuế/phí + phí hãng + điều chỉnh), cùng công thức pricing-service dùng khi hiển thị. */
  public Double calculateFareTotal(final UUID fareId) {
    final FareResponse fare = this.pricingClient.getFareById(fareId).getData();
    if (fare.getTotalPrice() != null) {
      return fare.getTotalPrice();
    }
    return orZero(fare.getBaseFare()) + orZero(fare.getTaxesAndFees()) + orZero(fare.getAirlineFees())
        + orZero(fare.getCurrentPrice());
  }

  private static double orZero(final Double value) {
    return value != null ? value : 0.0;
  }
}
