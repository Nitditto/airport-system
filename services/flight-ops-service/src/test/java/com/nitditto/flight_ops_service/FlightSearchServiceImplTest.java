package com.nitditto.flight_ops_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.client.PricingClient;
import com.nitditto.client.SeatClient;
import com.nitditto.enums.CabinClassType;
import com.nitditto.exception.BusinessException;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightInstance;
import com.nitditto.payload.request.FlightSearchRequest;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.CabinClassResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.service.impl.FlightSearchServiceImpl;
import com.nitditto.service.integration.FlightInstanceAssembler;
import com.nitditto.service.integration.ReferenceDataLoader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceImplTest {

  private static final LocalDate DATE = LocalDate.now().plusDays(1);

  @Mock
  private FlightInstanceRepository flightInstanceRepository;
  @Mock
  private FlightRepository flightRepository;
  @Mock
  private AirlineClient airlineClient;
  @Mock
  private LocationClient locationClient;
  @Mock
  private SeatClient seatClient;
  @Mock
  private PricingClient pricingClient;
  private FlightSearchServiceImpl service;

  private final UUID departureId = UUID.randomUUID();
  private final UUID arrivalId = UUID.randomUUID();
  private final UUID airlineId = UUID.randomUUID();
  private final UUID aircraftA = UUID.randomUUID();
  private final UUID aircraftB = UUID.randomUUID();
  private final UUID cabinA = UUID.randomUUID();
  private final UUID cabinB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    final ReferenceDataLoader loader = new ReferenceDataLoader(this.airlineClient, this.locationClient);
    this.service = new FlightSearchServiceImpl(this.flightInstanceRepository, this.flightRepository,
        this.locationClient, this.seatClient, this.pricingClient, loader, new FlightInstanceAssembler(loader));
  }

  @Test
  void search_usesFixedNumberOfRemoteCalls_attachesFare_andSortsByPrice() {
    final Flight flightA = flight(this.aircraftA);
    final Flight flightB = flight(this.aircraftB);
    final FlightInstance a1 = instance(flightA, 8, 10);
    final FlightInstance a2 = instance(flightA, 12, 14);
    final FlightInstance b1 = instance(flightB, 9, 11);
    stubDatabase(List.of(a1, a2, b1), flightA, flightB);
    stubCabins(Map.of(this.aircraftA, cabin(this.cabinA), this.aircraftB, cabin(this.cabinB)));
    when(this.pricingClient.getLowestFarePerFlight(any(), eq(this.cabinA))).thenReturn(ApiResponse.success(
        Map.of(flightA.getId(), fare(flightA, this.cabinA, 500.0))));
    when(this.pricingClient.getLowestFarePerFlight(any(), eq(this.cabinB))).thenReturn(ApiResponse.success(
        Map.of(flightB.getId(), fare(flightB, this.cabinB, 300.0))));
    this.stubReferenceData();

    final Page<FlightInstanceResponse> result = this.service.searchFlights(
        request().sortBy("price").build(), PageRequest.of(0, 20));

    assertThat(result.getContent()).extracting(r -> r.getFare().getTotalPrice())
        .containsExactly(300.0, 500.0, 500.0);
    assertThat(result.getContent().get(0).getId()).isEqualTo(b1.getId());
    assertThat(result.getTotalElements()).isEqualTo(3);
    // 1 call hạng ghế cho mọi máy bay, 1 call giá cho mỗi hạng ghế, 1 call batch cho mỗi loại dữ liệu tham chiếu.
    verify(this.seatClient, times(1)).getBookableCabinClasses(eq(CabinClassType.ECONOMY), any());
    verify(this.pricingClient, times(2)).getLowestFarePerFlight(any(), any());
    verify(this.airlineClient, times(1)).getAirlinesByIds(any());
    verify(this.airlineClient, times(1)).getAircraftsByIds(any());
    verify(this.locationClient, times(1)).getAirportsByIds(any());
    verify(this.locationClient, times(1)).getAirportById(this.departureId);
  }

  @Test
  void search_dropsInstancesWithoutCabinOrFare() {
    final Flight flightA = flight(this.aircraftA);
    final Flight flightB = flight(this.aircraftB);
    stubDatabase(List.of(instance(flightA, 8, 10), instance(flightB, 9, 11)), flightA, flightB);
    // Máy bay B không có hạng ghế này.
    stubCabins(Map.of(this.aircraftA, cabin(this.cabinA)));
    when(this.pricingClient.getLowestFarePerFlight(any(), eq(this.cabinA)))
        .thenReturn(ApiResponse.success(Map.of()));

    final Page<FlightInstanceResponse> result = this.service.searchFlights(
        request().build(), PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    verify(this.airlineClient, never()).getAirlinesByIds(any());
  }

  @Test
  void search_filtersByPriceDurationAndAlliance_inMemory() {
    final Flight flightA = flight(this.aircraftA);
    final FlightInstance cheapShort = instance(flightA, 8, 10);
    final FlightInstance cheapLong = instance(flightA, 9, 15);
    final FlightInstance second = instance(flightA, 10, 12);
    stubDatabase(List.of(cheapShort, cheapLong, second), flightA);
    stubCabins(Map.of(this.aircraftA, cabin(this.cabinA)));
    when(this.pricingClient.getLowestFarePerFlight(any(), eq(this.cabinA))).thenReturn(ApiResponse.success(
        Map.of(flightA.getId(), fare(flightA, this.cabinA, 500.0))));
    this.stubAirlines("Star Alliance");
    this.stubAircraftAndAirports();

    // Fare 500 nằm trong [100, 600]; chuyến 9h-15h (360 phút) vượt maxDuration 180 nên bị loại.
    final Page<FlightInstanceResponse> result = this.service.searchFlights(
        request().minPrice(100.0).maxPrice(600.0).maxDuration(180).alliance("star alliance").build(),
        PageRequest.of(0, 20));

    assertThat(result.getContent()).extracting(FlightInstanceResponse::getId)
        .containsExactly(cheapShort.getId(), second.getId());
    assertThat(result.getTotalElements()).isEqualTo(2);

    final Page<FlightInstanceResponse> tooCheap = this.service.searchFlights(
        request().minPrice(600.0).build(), PageRequest.of(0, 20));
    assertThat(tooCheap.getContent()).isEmpty();
  }

  @Test
  void search_paginatesInMemory_withCorrectTotal() {
    final Flight flightA = flight(this.aircraftA);
    final List<FlightInstance> instances = List.of(instance(flightA, 6, 8), instance(flightA, 7, 9),
        instance(flightA, 8, 10), instance(flightA, 9, 11), instance(flightA, 10, 12));
    stubDatabase(instances, flightA);
    stubCabins(Map.of(this.aircraftA, cabin(this.cabinA)));
    when(this.pricingClient.getLowestFarePerFlight(any(), eq(this.cabinA))).thenReturn(ApiResponse.success(
        Map.of(flightA.getId(), fare(flightA, this.cabinA, 500.0))));
    this.stubReferenceData();

    final Page<FlightInstanceResponse> page = this.service.searchFlights(
        request().build(), PageRequest.of(1, 2));

    assertThat(page.getContent()).extracting(FlightInstanceResponse::getId)
        .containsExactly(instances.get(2).getId(), instances.get(3).getId());
    assertThat(page.getTotalElements()).isEqualTo(5);
    assertThat(page.getTotalPages()).isEqualTo(3);
  }

  @Test
  void search_returnsEmptyWithoutOtherCalls_whenDatabaseHasNoCandidates() {
    when(this.locationClient.getAirportById(this.departureId)).thenReturn(
        ApiResponse.success(AirportResponse.builder().build()));
    when(this.flightInstanceRepository.findAll(anySpec(), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(Page.empty());

    final Page<FlightInstanceResponse> result = this.service.searchFlights(
        request().build(), PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    verifyNoInteractions(this.seatClient, this.pricingClient, this.airlineClient, this.flightRepository);
  }

  @Test
  void search_rejectsInvalidRequests_beforeCallingAnything() {
    assertThatThrownBy(() -> this.service.searchFlights(
        request().minPrice(900.0).maxPrice(100.0).build(), PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> this.service.searchFlights(
        request().arrivalAirportId(this.departureId).build(), PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(this.locationClient, this.flightInstanceRepository);
  }

  @Test
  void search_rejectsUnknownSortKey() {
    final Flight flightA = flight(this.aircraftA);
    stubDatabase(List.of(instance(flightA, 8, 10)), flightA);
    stubCabins(Map.of(this.aircraftA, cabin(this.cabinA)));
    when(this.pricingClient.getLowestFarePerFlight(any(), eq(this.cabinA))).thenReturn(ApiResponse.success(
        Map.of(flightA.getId(), fare(flightA, this.cabinA, 500.0))));

    assertThatThrownBy(() -> this.service.searchFlights(
        request().sortBy("colour").build(), PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("sortBy");
  }

  // --- helpers ---

  private FlightSearchRequest.FlightSearchRequestBuilder request() {
    return FlightSearchRequest.builder().departureAirportId(this.departureId).arrivalAirportId(this.arrivalId)
        .departureDate(DATE).passengers(2).cabinClass(CabinClassType.ECONOMY);
  }

  private void stubDatabase(final List<FlightInstance> instances, final Flight... flights) {
    when(this.locationClient.getAirportById(this.departureId)).thenReturn(
        ApiResponse.success(AirportResponse.builder().build()));
    when(this.flightInstanceRepository.findAll(anySpec(), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new PageImpl<>(instances));
    when(this.flightRepository.findAllById(any())).thenReturn(List.of(flights));
  }

  private void stubCabins(final Map<UUID, CabinClassResponse> cabinByAircraftId) {
    when(this.seatClient.getBookableCabinClasses(eq(CabinClassType.ECONOMY), any()))
        .thenReturn(ApiResponse.success(cabinByAircraftId));
  }

  private void stubAirlines(final String alliance) {
    when(this.airlineClient.getAirlinesByIds(any())).thenReturn(ApiResponse.success(Map.of(this.airlineId,
        AirlineResponse.builder().id(this.airlineId).name("SkyAir").alliance(alliance).build())));
  }

  private void stubReferenceData() {
    this.stubAirlines(null);
    this.stubAircraftAndAirports();
  }

  private void stubAircraftAndAirports() {
    when(this.airlineClient.getAircraftsByIds(any())).thenReturn(ApiResponse.success(Map.of()));
    when(this.locationClient.getAirportsByIds(any())).thenReturn(ApiResponse.success(Map.of()));
  }

  private static Specification<FlightInstance> anySpec() {
    return org.mockito.ArgumentMatchers.<Specification<FlightInstance>>any();
  }

  private Flight flight(final UUID aircraftId) {
    final Flight flight = Flight.builder().airlineId(this.airlineId).aircraftId(aircraftId).build();
    flight.setId(UUID.randomUUID());
    return flight;
  }

  private FlightInstance instance(final Flight flight, final int departHour, final int arriveHour) {
    final FlightInstance instance = FlightInstance.builder().flightId(flight.getId()).airlineId(this.airlineId)
        .departureAirportId(this.departureId).arrivalAirportId(this.arrivalId)
        .departureDateTime(DATE.atTime(departHour, 0)).arrivalDateTime(DATE.atTime(arriveHour, 0)).build();
    instance.setId(UUID.randomUUID());
    return instance;
  }

  private static CabinClassResponse cabin(final UUID id) {
    return CabinClassResponse.builder().id(id).isActive(true).isBookable(true).build();
  }

  private static FareResponse fare(final Flight flight, final UUID cabinId, final double totalPrice) {
    return FareResponse.builder().id(UUID.randomUUID()).flightId(flight.getId()).cabinClassId(cabinId)
        .totalPrice(totalPrice).build();
  }
}
