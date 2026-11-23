package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nitditto.client.FlightClient;
import com.nitditto.client.PricingClient;
import com.nitditto.enums.CabinClassType;
import com.nitditto.enums.FlightStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.service.integration.BookingTargetResolver;
import com.nitditto.service.integration.BookingTargetResolver.BookingTarget;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class BookingTargetResolverTest {

  @Mock
  private FlightClient flightClient;
  @Mock
  private PricingClient pricingClient;

  private BookingTargetResolver resolver;

  private final UUID flightId = UUID.randomUUID();
  private final UUID instanceId = UUID.randomUUID();
  private final UUID fareId = UUID.randomUUID();
  private final UUID airlineId = UUID.randomUUID();
  private final UUID economyClassId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.resolver = new BookingTargetResolver(this.flightClient, this.pricingClient);
  }

  @Test
  void consistentRequest_resolvesAirlineFromTheFlightInstance_andKeepsTheFare() {
    this.givenInstance(this.flightId, FlightStatus.SCHEDULED);
    this.givenFare(this.flightId, CabinClassType.ECONOMY);

    final BookingTarget target = this.resolver.resolve(this.request());

    assertThat(target.airlineId()).isEqualTo(this.airlineId);
    assertThat(target.flightInstanceId()).isEqualTo(this.instanceId);
    assertThat(target.fare().getCabinClassId()).isEqualTo(this.economyClassId);
  }

  @Test
  void flightInstanceOfAnotherFlight_isRejected() {
    this.givenInstance(UUID.randomUUID(), FlightStatus.SCHEDULED);

    assertThatThrownBy(() -> this.resolver.resolve(this.request()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("Flight instance does not belong");
  }

  @Test
  void departedFlightInstance_isRejected() {
    this.givenInstance(this.flightId, FlightStatus.DEPARTED);

    assertThatThrownBy(() -> this.resolver.resolve(this.request()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("no longer open");
  }

  @Test
  void fareOfAnotherFlight_isRejected() {
    this.givenInstance(this.flightId, FlightStatus.SCHEDULED);
    this.givenFare(UUID.randomUUID(), CabinClassType.ECONOMY);

    assertThatThrownBy(() -> this.resolver.resolve(this.request()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("Fare does not belong");
  }

  @Test
  void fareOfAnotherCabinClass_isRejected() {
    this.givenInstance(this.flightId, FlightStatus.SCHEDULED);
    this.givenFare(this.flightId, CabinClassType.BUSINESS);

    assertThatThrownBy(() -> this.resolver.resolve(this.request()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("different cabin class");
  }

  @Test
  void unknownFlightInstance_isNotFound() {
    when(this.flightClient.getFlightInstance(this.instanceId)).thenReturn(ResponseEntity.ok(ApiResponse.success(null)));

    assertThatThrownBy(() -> this.resolver.resolve(this.request())).isInstanceOf(ResourceNotFoundException.class);
  }

  private BookingRequest request() {
    return BookingRequest.builder().flightId(this.flightId).flightInstanceId(this.instanceId).fareId(this.fareId)
        .cabinClassType(CabinClassType.ECONOMY).build();
  }

  private void givenInstance(final UUID ofFlight, final FlightStatus status) {
    final FlightInstanceResponse instance = FlightInstanceResponse.builder().id(this.instanceId).flightId(ofFlight)
        .airlineId(this.airlineId).status(status).isActive(true).build();
    when(this.flightClient.getFlightInstance(this.instanceId)).thenReturn(ResponseEntity.ok(ApiResponse.success(instance)));
  }

  private void givenFare(final UUID ofFlight, final CabinClassType cabin) {
    final FareResponse fare = FareResponse.builder().id(this.fareId).flightId(ofFlight).cabinClassType(cabin)
        .cabinClassId(this.economyClassId).build();
    when(this.pricingClient.getFareById(this.fareId)).thenReturn(ApiResponse.success(fare));
  }
}
