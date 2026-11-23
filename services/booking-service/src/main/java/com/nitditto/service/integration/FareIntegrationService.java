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
    return this.farePerPassenger(this.pricingClient.getFareById(fareId).getData());
  }

  /** Như {@link #calculateFareTotal(UUID)} nhưng dùng fare đã tải (và đã xác thực) sẵn, không gọi lại pricing-service. */
  public Double farePerPassenger(final FareResponse fare) {
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
