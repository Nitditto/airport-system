package com.nitditto.flight_ops_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightInstance;
import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.service.impl.FlightInstanceServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightInstanceServiceImplTest {

  @Mock
  private FlightRepository flightRepository;
  @Mock
  private FlightInstanceRepository flightInstanceRepository;
  @Mock
  private AirlineClient airlineClient;
  @Mock
  private LocationClient locationClient;
  @InjectMocks
  private FlightInstanceServiceImpl service;

  @Captor
  private ArgumentCaptor<List<FlightInstance>> savedCaptor;

  private final UUID airlineId = UUID.randomUUID();
  private final UUID aircraftId = UUID.randomUUID();

  @Test
  void createFlightInstances_usesOneAircraftLookupAndOneSaveAll_forManyInstances() {
    final Flight flight = flight(this.airlineId);
    final List<FlightInstanceRequest> requests = List.of(
        request(flight.getId()), request(flight.getId()), request(flight.getId()));
    when(this.flightRepository.findAllById(List.of(flight.getId()))).thenReturn(List.of(flight));
    when(this.airlineClient.getAircraftsByIds(List.of(this.aircraftId))).thenReturn(
        ApiResponse.success(Map.of(this.aircraftId, AircraftResponse.builder().totalSeats(180).build())));

    this.service.createFlightInstances(this.airlineId, requests);

    verify(this.flightInstanceRepository).saveAll(this.savedCaptor.capture());
    assertThat(this.savedCaptor.getValue()).hasSize(3)
        .allSatisfy(instance -> {
          assertThat(instance.getTotalSeats()).isEqualTo(180);
          assertThat(instance.getAvailableSeats()).isEqualTo(180);
        });
    verify(this.airlineClient).getAircraftsByIds(any());
    verify(this.flightInstanceRepository, never()).save(any());
  }

  @Test
  void createFlightInstances_rejectsFlightOfAnotherAirline_withoutCallingAircraftOrSaving() {
    final Flight foreignFlight = flight(UUID.randomUUID());
    when(this.flightRepository.findAllById(List.of(foreignFlight.getId())))
        .thenReturn(List.of(foreignFlight));

    assertThatThrownBy(() -> this.service.createFlightInstances(
        this.airlineId, List.of(request(foreignFlight.getId()))))
        .isInstanceOf(ResourceNotFoundException.class);

    verifyNoInteractions(this.airlineClient);
    verify(this.flightInstanceRepository, never()).saveAll(any());
  }

  @Test
  void createFlightInstances_rejectsUnknownFlight() {
    final UUID unknownFlightId = UUID.randomUUID();
    when(this.flightRepository.findAllById(List.of(unknownFlightId))).thenReturn(List.of());

    assertThatThrownBy(() -> this.service.createFlightInstances(
        this.airlineId, List.of(request(unknownFlightId))))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void createFlightInstances_doesNothing_whenThereAreNoRequests() {
    this.service.createFlightInstances(this.airlineId, List.of());

    verifyNoInteractions(this.flightRepository, this.airlineClient, this.flightInstanceRepository);
  }

  private Flight flight(final UUID ownerAirlineId) {
    final Flight flight = Flight.builder().airlineId(ownerAirlineId).aircraftId(this.aircraftId).build();
    flight.setId(UUID.randomUUID());
    return flight;
  }

  private FlightInstanceRequest request(final UUID flightId) {
    return FlightInstanceRequest.builder().flightId(flightId).build();
  }
}
