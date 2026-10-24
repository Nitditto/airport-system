package com.nitditto.flight_ops_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.service.impl.FlightInstanceServiceImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

  @Test
  void searchPublic_usesFixedNumberOfLookups_regardlessOfInstanceCount() {
    final UUID departureId = UUID.randomUUID();
    final UUID arrivalId = UUID.randomUUID();
    final Flight flight = flight(this.airlineId);
    final List<FlightInstance> instances = List.of(
        instance(flight, departureId, arrivalId), instance(flight, departureId, arrivalId),
        instance(flight, departureId, arrivalId));
    when(this.flightInstanceRepository.searchOpenForSale(any(), any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(new PageImpl<>(instances));
    when(this.flightRepository.findAllById(any())).thenReturn(List.of(flight));
    when(this.airlineClient.getAirlinesByIds(any())).thenReturn(ApiResponse.success(
        Map.of(this.airlineId, AirlineResponse.builder().name("SkyAir").build())));
    when(this.airlineClient.getAircraftsByIds(any())).thenReturn(ApiResponse.success(
        Map.of(this.aircraftId, AircraftResponse.builder().model("A320").build())));
    when(this.locationClient.getAirportById(any())).thenReturn(
        ApiResponse.success(AirportResponse.builder().build()));

    final Page<FlightInstanceResponse> result = this.service.searchPublic(
        departureId, arrivalId, LocalDate.now().plusDays(1), 2, PageRequest.of(0, 50));

    assertThat(result.getContent()).hasSize(3)
        .allSatisfy(r -> {
          assertThat(r.getAirlineName()).isEqualTo("SkyAir");
          assertThat(r.getAircraftId()).isEqualTo(this.aircraftId);
        });
    verify(this.airlineClient, times(1)).getAirlinesByIds(any());
    verify(this.airlineClient, times(1)).getAircraftsByIds(any());
    verify(this.locationClient, times(2)).getAirportById(any());
    verify(this.airlineClient, never()).getAirlineByOwner(any());
  }

  @Test
  void searchPublic_returnsEmptyWithoutCallingOtherServices_whenNothingMatches() {
    when(this.flightInstanceRepository.searchOpenForSale(any(), any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(Page.empty());
    when(this.locationClient.getAirportById(any())).thenReturn(
        ApiResponse.success(AirportResponse.builder().build()));

    final Page<FlightInstanceResponse> result = this.service.searchPublic(
        UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), 1, PageRequest.of(0, 50));

    assertThat(result.getContent()).isEmpty();
    verifyNoInteractions(this.airlineClient);
  }

  @Test
  void searchPublic_comparesDepartureWithNowInTheDepartureAirportTimeZone() {
    final ZoneId farAhead = ZoneId.of("Pacific/Kiritimati"); // UTC+14, lệch xa mọi múi giờ server
    when(this.locationClient.getAirportById(any())).thenReturn(
        ApiResponse.success(AirportResponse.builder().timeZone(farAhead).build()));
    when(this.flightInstanceRepository.searchOpenForSale(any(), any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(Page.empty());

    this.service.searchPublic(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), 1, PageRequest.of(0, 50));

    final ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(this.flightInstanceRepository).searchOpenForSale(any(), any(), any(), any(), now.capture(),
        anyInt(), any());
    assertThat(now.getValue()).isCloseTo(LocalDateTime.now(farAhead), within(1, ChronoUnit.MINUTES));
  }

  @Test
  void searchPublic_passesPassengerCount_andCapsPageSize() {
    when(this.locationClient.getAirportById(any())).thenReturn(
        ApiResponse.success(AirportResponse.builder().build()));
    when(this.flightInstanceRepository.searchOpenForSale(any(), any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(Page.empty());

    this.service.searchPublic(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), 3,
        PageRequest.of(0, 100_000));

    final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(this.flightInstanceRepository).searchOpenForSale(any(), any(), any(), any(), any(), eq(3),
        pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
  }

  private FlightInstance instance(final Flight flight, final UUID departureId, final UUID arrivalId) {
    return FlightInstance.builder().flightId(flight.getId()).airlineId(this.airlineId)
        .departureAirportId(departureId).arrivalAirportId(arrivalId).build();
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
