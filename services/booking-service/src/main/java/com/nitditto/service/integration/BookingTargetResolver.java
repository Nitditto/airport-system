package com.nitditto.service.integration;

import com.nitditto.client.FlightClient;
import com.nitditto.client.PricingClient;
import com.nitditto.enums.FlightStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Đối chiếu các id client gửi trong BookingRequest trước khi giữ ghế: flight instance phải thuộc đúng flight và còn
 * mở bán, fare phải thuộc đúng flight và đúng hạng ghế. Không có bước này, khách ghép được fare rẻ của chuyến/hạng khác.
 * Hãng của booking lấy từ flight instance đã xác thực, không suy từ flightId client gửi.
 */
@Component
@RequiredArgsConstructor
public class BookingTargetResolver {

  private final FlightClient flightClient;
  private final PricingClient pricingClient;

  /** Chuyến và fare đã xác thực của một booking. */
  public record BookingTarget(UUID airlineId, UUID flightInstanceId, FareResponse fare) {
  }

  public BookingTarget resolve(final BookingRequest request) {
    final FlightInstanceResponse instance = this.flightInstance(request.getFlightInstanceId());
    if (!request.getFlightId().equals(instance.getFlightId())) {
      throw new BusinessException("Flight instance does not belong to the requested flight");
    }
    if (instance.getStatus() != FlightStatus.SCHEDULED || Boolean.FALSE.equals(instance.getIsActive())) {
      throw new BusinessException("Flight instance is no longer open for booking");
    }
    if (instance.getAirlineId() == null) {
      throw new ResourceNotFoundException("Flight", "id", request.getFlightId());
    }

    final FareResponse fare = this.fare(request.getFareId());
    if (!request.getFlightId().equals(fare.getFlightId())) {
      throw new BusinessException("Fare does not belong to the requested flight");
    }
    if (fare.getCabinClassType() != null && fare.getCabinClassType() != request.getCabinClassType()) {
      throw new BusinessException("Fare is for a different cabin class than requested");
    }
    return new BookingTarget(instance.getAirlineId(), instance.getId(), fare);
  }

  private FlightInstanceResponse flightInstance(final UUID id) {
    final ResponseEntity<ApiResponse<FlightInstanceResponse>> response = this.flightClient.getFlightInstance(id);
    final FlightInstanceResponse instance = response == null || response.getBody() == null ? null
        : response.getBody().getData();
    if (instance == null) {
      throw new ResourceNotFoundException("FlightInstance", "id", id);
    }
    return instance;
  }

  private FareResponse fare(final UUID id) {
    final FareResponse fare = this.pricingClient.getFareById(id).getData();
    if (fare == null) {
      throw new ResourceNotFoundException("Fare", "id", id);
    }
    return fare;
  }
}
