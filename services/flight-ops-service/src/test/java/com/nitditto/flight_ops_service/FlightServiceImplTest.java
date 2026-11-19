package com.nitditto.flight_ops_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.model.Flight;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.repository.FlightScheduleRepository;
import com.nitditto.service.impl.FlightServiceImpl;
import com.nitditto.service.integration.ReferenceDataLoader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

  @Mock
  private FlightRepository flightRepository;
  @Mock
  private FlightScheduleRepository flightScheduleRepository;
  @Mock
  private FlightInstanceRepository flightInstanceRepository;
  @Mock
  private AirlineClient airlineClient;
  @Mock
  private LocationClient locationClient;
  private FlightServiceImpl service;

  private final UUID ownerId = UUID.randomUUID();
  private final UUID airlineId = UUID.randomUUID();
  private final UUID aircraftId = UUID.randomUUID();
  private final UUID departureId = UUID.randomUUID();
  private final UUID arrivalId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new FlightServiceImpl(this.flightRepository, this.flightScheduleRepository,
        this.flightInstanceRepository, this.airlineClient,
        new ReferenceDataLoader(this.airlineClient, this.locationClient));
  }

  @Test
  void getFlightsByAirline_usesFixedNumberOfRemoteCalls_regardlessOfPageSize() {
    final List<Flight> flights = IntStream.range(0, 20).mapToObj(i -> this.flight()).toList();
    when(this.airlineClient.getAirlineByOwner(this.ownerId)).thenReturn(
        ApiResponse.success(AirlineResponse.builder().id(this.airlineId).build()));
    when(this.flightRepository.findByAirlineId(any(), any(), any(), any())).thenReturn(new PageImpl<>(flights));
    when(this.airlineClient.getAirlinesByIds(anyList())).thenReturn(ApiResponse.success(
        Map.of(this.airlineId, AirlineResponse.builder().id(this.airlineId).name("SkyAir").build())));
    when(this.airlineClient.getAircraftsByIds(anyList())).thenReturn(ApiResponse.success(
        Map.of(this.aircraftId, AircraftResponse.builder().id(this.aircraftId).model("A320").build())));
    when(this.locationClient.getAirportsByIds(anyList())).thenReturn(ApiResponse.success(Map.of(
        this.departureId, AirportResponse.builder().id(this.departureId).build(),
        this.arrivalId, AirportResponse.builder().id(this.arrivalId).build())));

    final Page<FlightResponse> result = this.service.getFlightsByAirline(
        this.ownerId, null, null, PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(20).allSatisfy(response -> {
      assertThat(response.getAirline().getName()).isEqualTo("SkyAir");
      assertThat(response.getAircraft().getModel()).isEqualTo("A320");
      assertThat(response.getDepartureAirport().getId()).isEqualTo(this.departureId);
      assertThat(response.getArrivalAirport().getId()).isEqualTo(this.arrivalId);
    });
    verify(this.airlineClient, times(1)).getAirlinesByIds(List.of(this.airlineId));
    verify(this.airlineClient, times(1)).getAircraftsByIds(List.of(this.aircraftId));
    verify(this.locationClient, times(1)).getAirportsByIds(List.of(this.departureId, this.arrivalId));
    verify(this.airlineClient, never()).getAirlineById(any());
    verify(this.airlineClient, never()).getAircraftById(any());
    verify(this.locationClient, never()).getAirportById(any());
  }

  @Test
  void getFlightsByAirline_doesNotCallReferenceServices_whenPageIsEmpty() {
    when(this.airlineClient.getAirlineByOwner(this.ownerId)).thenReturn(
        ApiResponse.success(AirlineResponse.builder().id(this.airlineId).build()));
    when(this.flightRepository.findByAirlineId(any(), any(), any(), any())).thenReturn(Page.empty());

    final Page<FlightResponse> result = this.service.getFlightsByAirline(
        this.ownerId, null, null, PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    verify(this.airlineClient, never()).getAirlinesByIds(any());
    verify(this.airlineClient, never()).getAircraftsByIds(any());
    verify(this.locationClient, never()).getAirportsByIds(any());
  }

  private Flight flight() {
    final Flight flight = Flight.builder().airlineId(this.airlineId).aircraftId(this.aircraftId)
        .departureAirportId(this.departureId).arrivalAirportId(this.arrivalId).build();
    flight.setId(UUID.randomUUID());
    return flight;
  }
}
