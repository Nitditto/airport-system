package com.nitditto.seat_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.config.SeatPricingProperties;
import com.nitditto.enums.SeatAvailabilityStatus;
import com.nitditto.enums.SeatType;
import com.nitditto.model.CabinClass;
import com.nitditto.model.FlightInstanceCabin;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatInstance;
import com.nitditto.model.SeatMap;
import com.nitditto.repository.CabinClassRepository;
import com.nitditto.repository.FlightInstanceCabinRepository;
import com.nitditto.repository.SeatInstanceRepository;
import com.nitditto.repository.SeatMapRepository;
import com.nitditto.repository.SeatRepository;
import com.nitditto.service.impl.FlightInstanceCabinServiceImpl;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightInstanceCabinServiceImplTest {

  @Mock
  private FlightInstanceCabinRepository cabinRepository;
  @Mock
  private CabinClassRepository cabinClassRepository;
  @Mock
  private SeatMapRepository seatMapRepository;
  @Mock
  private SeatRepository seatRepository;
  @Mock
  private SeatInstanceRepository seatInstanceRepository;
  @Captor
  private ArgumentCaptor<List<SeatInstance>> seatInstancesCaptor;
  @Captor
  private ArgumentCaptor<FlightInstanceCabin> cabinCaptor;

  private FlightInstanceCabinServiceImpl service;

  private final UUID flightInstanceId = UUID.randomUUID();
  private final UUID flightId = UUID.randomUUID();
  private final UUID aircraftId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new FlightInstanceCabinServiceImpl(this.cabinRepository, this.cabinClassRepository,
        this.seatMapRepository, this.seatRepository, this.seatInstanceRepository, Clock.systemUTC(),
        new SeatPricingProperties(1000.0, 500.0));
  }

  @Test
  void generate_appliesSurchargePriority_seatOwnPriceThenTypeDefaultThenZero() {
    final CabinClass economy = cabinClass();
    final SeatMap seatMap = seatMapOf(economy);
    when(this.cabinClassRepository.findByAircraftId(this.aircraftId)).thenReturn(List.of(economy));
    when(this.seatMapRepository.findByCabinClassId(economy.getId())).thenReturn(Optional.of(seatMap));
    when(this.seatRepository.findBySeatMapId(seatMap.getId())).thenReturn(List.of(
        seat(SeatType.WINDOW, 2000.0), // giá riêng của ghế mẫu thắng mặc định
        seat(SeatType.WINDOW, null),
        seat(SeatType.AISLE, null),
        seat(SeatType.MIDDLE, null)));
    givenCabinSaveAssignsId();

    final int created = this.service.generateForFlightInstance(this.flightInstanceId, this.flightId, this.aircraftId);

    assertThat(created).isEqualTo(4);
    verify(this.seatInstanceRepository).saveAll(this.seatInstancesCaptor.capture());
    final List<SeatInstance> instances = this.seatInstancesCaptor.getValue();
    assertThat(instances).extracting(SeatInstance::getPremiumSuperCharge).containsExactly(2000.0, 1000.0, 500.0, 0.0);
    // baseFare = 0 nên SeatInstance.fare chỉ còn là phí chọn chỗ, không phải giá vé
    assertThat(instances).allSatisfy(si -> {
      assertThat(si.getFare()).isEqualTo(si.getPremiumSuperCharge());
      assertThat(si.getStatus()).isEqualTo(SeatAvailabilityStatus.AVAILABLE);
      assertThat(si.getFlightInstanceId()).isEqualTo(this.flightInstanceId);
      assertThat(si.getSeatMapId()).isEqualTo(seatMap.getId());
    });

    verify(this.cabinRepository).save(this.cabinCaptor.capture());
    final FlightInstanceCabin cabin = this.cabinCaptor.getValue();
    assertThat(cabin.getBaseFare()).isZero();
    assertThat(cabin.getTotalSeats()).isEqualTo(4);
    assertThat(cabin.getWindowSurcharge()).isEqualTo(1000.0);
    assertThat(cabin.getAisleSurcharge()).isEqualTo(500.0);
  }

  @Test
  void generate_isIdempotent_whenCabinAlreadyExistsForTheFlightInstance() {
    final CabinClass economy = cabinClass();
    when(this.cabinClassRepository.findByAircraftId(this.aircraftId)).thenReturn(List.of(economy));
    when(this.cabinRepository.existsByFlightInstanceIdAndCabinClassId(this.flightInstanceId, economy.getId()))
        .thenReturn(true);

    final int created = this.service.generateForFlightInstance(this.flightInstanceId, this.flightId, this.aircraftId);

    assertThat(created).isZero();
    verify(this.cabinRepository, never()).save(any());
    verify(this.seatInstanceRepository, never()).saveAll(anyList());
  }

  @Test
  void generate_skipsCabinWithoutSeatMap_butStillGeneratesTheOthers() {
    final CabinClass business = cabinClass();
    final CabinClass economy = cabinClass();
    final SeatMap economyMap = seatMapOf(economy);
    when(this.cabinClassRepository.findByAircraftId(this.aircraftId)).thenReturn(List.of(business, economy));
    when(this.seatMapRepository.findByCabinClassId(business.getId())).thenReturn(Optional.empty());
    when(this.seatMapRepository.findByCabinClassId(economy.getId())).thenReturn(Optional.of(economyMap));
    when(this.seatRepository.findBySeatMapId(economyMap.getId())).thenReturn(List.of(seat(SeatType.MIDDLE, null)));
    givenCabinSaveAssignsId();

    final int created = this.service.generateForFlightInstance(this.flightInstanceId, this.flightId, this.aircraftId);

    assertThat(created).isEqualTo(1);
    verify(this.cabinRepository).save(this.cabinCaptor.capture());
    assertThat(this.cabinCaptor.getValue().getCabinClassId()).isEqualTo(economy.getId());
  }

  private void givenCabinSaveAssignsId() {
    when(this.cabinRepository.save(any(FlightInstanceCabin.class))).thenAnswer(invocation -> {
      final FlightInstanceCabin cabin = invocation.getArgument(0);
      cabin.setId(UUID.randomUUID());
      return cabin;
    });
  }

  private CabinClass cabinClass() {
    final CabinClass cabinClass = CabinClass.builder().aircraftId(this.aircraftId).code("Y").build();
    cabinClass.setId(UUID.randomUUID());
    return cabinClass;
  }

  private static SeatMap seatMapOf(final CabinClass cabinClass) {
    final SeatMap seatMap = SeatMap.builder().name("map").cabinClassId(cabinClass.getId()).build();
    seatMap.setId(UUID.randomUUID());
    return seatMap;
  }

  private static Seat seat(final SeatType type, final Double ownSurcharge) {
    final Seat seat = Seat.builder().seatType(type).premiumSuperCharge(ownSurcharge).build();
    seat.setId(UUID.randomUUID());
    return seat;
  }
}
