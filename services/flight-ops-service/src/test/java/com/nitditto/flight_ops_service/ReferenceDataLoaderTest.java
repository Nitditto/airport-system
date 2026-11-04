package com.nitditto.flight_ops_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.service.integration.ReferenceData;
import com.nitditto.service.integration.ReferenceDataLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataLoaderTest {

  @Mock
  private AirlineClient airlineClient;
  @Mock
  private LocationClient locationClient;
  private ReferenceDataLoader loader;

  @BeforeEach
  void setUp() {
    this.loader = new ReferenceDataLoader(this.airlineClient, this.locationClient);
  }

  @Test
  void load_skipsRemoteCalls_whenThereAreNoIds() {
    final ReferenceData data = this.loader.load(List.of(), Arrays.asList(null, null), null);

    assertThat(data.airline(UUID.randomUUID())).isNull();
    verifyNoInteractions(this.airlineClient, this.locationClient);
  }

  @Test
  void load_sendsDistinctNonNullIds_inOneCallPerType() {
    final UUID airportId = UUID.randomUUID();
    when(this.locationClient.getAirportsByIds(List.of(airportId))).thenReturn(
        ApiResponse.success(Map.of(airportId, AirportResponse.builder().id(airportId).build())));

    final ReferenceData data = this.loader.load(List.of(), List.of(),
        Arrays.asList(airportId, null, airportId, airportId));

    assertThat(data.airport(airportId).getId()).isEqualTo(airportId);
    assertThat(data.airport(null)).isNull();
    verify(this.locationClient, times(1)).getAirportsByIds(List.of(airportId));
    verifyNoInteractions(this.airlineClient);
  }

  @Test
  @SuppressWarnings("unchecked")
  void load_splitsLargeRequestsIntoBatchesOf200() {
    final List<UUID> ids = IntStream.range(0, 450).mapToObj(i -> UUID.randomUUID()).toList();
    when(this.airlineClient.getAirlinesByIds(anyList())).thenAnswer(invocation -> {
      final Map<UUID, AirlineResponse> found = new HashMap<>();
      for (final UUID id : (List<UUID>) invocation.getArgument(0)) {
        found.put(id, AirlineResponse.builder().id(id).build());
      }
      return ApiResponse.success(found);
    });

    final ReferenceData data = this.loader.load(ids, List.of(), List.of());

    final ArgumentCaptor<List<UUID>> batches = ArgumentCaptor.forClass(List.class);
    verify(this.airlineClient, times(3)).getAirlinesByIds(batches.capture());
    assertThat(batches.getAllValues()).extracting(List::size).containsExactly(200, 200, 50);
    assertThat(data.airlines()).hasSize(450);
  }

  @Test
  void load_ignoresNullEntriesInUpstreamResponse() {
    final UUID found = UUID.randomUUID();
    final UUID missing = UUID.randomUUID();
    final Map<UUID, AirportResponse> body = new HashMap<>();
    body.put(found, AirportResponse.builder().id(found).build());
    body.put(missing, null);
    when(this.locationClient.getAirportsByIds(anyList())).thenReturn(ApiResponse.success(body));

    final ReferenceData data = this.loader.load(List.of(), List.of(), List.of(found, missing));

    assertThat(data.airport(found)).isNotNull();
    assertThat(data.airport(missing)).isNull();
  }

  @Test
  void load_treatsMissingDataAsEmpty() {
    final UUID airlineId = UUID.randomUUID();
    when(this.airlineClient.getAirlinesByIds(anyList())).thenReturn(ApiResponse.success(null));

    final ReferenceData data = this.loader.load(new ArrayList<>(List.of(airlineId)), List.of(), List.of());

    assertThat(data.airline(airlineId)).isNull();
  }
}
