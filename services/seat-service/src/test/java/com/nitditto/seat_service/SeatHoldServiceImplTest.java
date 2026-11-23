package com.nitditto.seat_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.enums.SeatAvailabilityStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.SeatInstance;
import com.nitditto.payload.response.SeatHoldResponse;
import com.nitditto.repository.FlightInstanceCabinRepository;
import com.nitditto.repository.SeatInstanceRepository;
import com.nitditto.service.SeatHoldScope;
import com.nitditto.service.impl.SeatHoldServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

  @Mock
  private SeatInstanceRepository seatRepository;
  @Mock
  private FlightInstanceCabinRepository cabinRepository;

  private SeatHoldServiceImpl service;

  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();
  private final UUID flightInstanceId = UUID.randomUUID();
  private final UUID cabinId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new SeatHoldServiceImpl(this.seatRepository, this.cabinRepository,
        Clock.fixed(NOW, ZoneOffset.UTC), 15);
  }

  @Test
  void hold_marksSeatsHeldForTheUser_untilTtlFromNow() {
    final SeatInstance seat = seat();
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));

    final SeatHoldResponse response = this.service.hold(this.alice, List.of(seat.getId()));

    assertThat(response.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(seat.getHeldBy()).isEqualTo(this.alice);
    assertThat(seat.getHeldUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
  }

  @Test
  void hold_rejectsSeatHeldByAnotherUser_andChangesNothing() {
    final SeatInstance seat = seat();
    seat.setHeldBy(this.bob);
    seat.setHeldUntil(NOW.plusSeconds(300));
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));

    assertThatThrownBy(() -> this.service.hold(this.alice, List.of(seat.getId())))
        .isInstanceOf(BusinessException.class).hasMessageContaining("no longer available");

    assertThat(seat.getHeldBy()).isEqualTo(this.bob);
    verify(this.seatRepository, never()).saveAll(any());
  }

  @Test
  void hold_allowsTakingOverSeatWhoseHoldHasExpired() {
    final SeatInstance seat = seat();
    seat.setHeldBy(this.bob);
    seat.setHeldUntil(NOW.minusSeconds(1));
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));

    this.service.hold(this.alice, List.of(seat.getId()));

    assertThat(seat.getHeldBy()).isEqualTo(this.alice);
  }

  @Test
  void hold_isAllOrNothing_whenOneSeatIsTaken() {
    final SeatInstance free = seat();
    final SeatInstance taken = seat();
    taken.setStatus(SeatAvailabilityStatus.BOOKED);
    taken.setBooked(true);
    when(this.seatRepository.lockAllByIdIn(List.of(free.getId(), taken.getId())))
        .thenReturn(List.of(free, taken));

    assertThatThrownBy(() -> this.service.hold(this.alice, List.of(free.getId(), taken.getId())))
        .isInstanceOf(BusinessException.class);

    assertThat(free.getHeldBy()).isNull();
  }

  @Test
  void hold_releasesSeatsTheUserHeldEarlierOnTheSameFlight_whenTheyChangeSeats() {
    final SeatInstance newSeat = seat();
    final SeatInstance oldSeat = seat();
    oldSeat.setHeldBy(this.alice);
    oldSeat.setHeldUntil(NOW.plusSeconds(600));
    when(this.seatRepository.lockAllByIdIn(List.of(newSeat.getId()))).thenReturn(List.of(newSeat));
    when(this.seatRepository.findByFlightInstanceIdInAndHeldBy(any(), any())).thenReturn(List.of(oldSeat, newSeat));

    this.service.hold(this.alice, List.of(newSeat.getId()));

    assertThat(oldSeat.getHeldBy()).isNull();
    assertThat(newSeat.getHeldBy()).isEqualTo(this.alice);
  }

  @Test
  void hold_rejectsSeatsOfAnotherFlightInstance_andHoldsNothing() {
    final SeatInstance seat = seat();
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));

    assertThatThrownBy(() -> this.service.hold(this.alice, List.of(seat.getId()),
        new SeatHoldScope(UUID.randomUUID(), null)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("flight instance");

    assertThat(seat.getHeldBy()).isNull();
    verify(this.seatRepository, never()).saveAll(any());
  }

  @Test
  void hold_rejectsSeatsOutsideTheFareCabinClass() {
    final SeatInstance seat = seat();
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));
    when(this.cabinRepository.findAllById(any())).thenReturn(List.of(cabinOfClass(UUID.randomUUID())));

    assertThatThrownBy(() -> this.service.hold(this.alice, List.of(seat.getId()),
        new SeatHoldScope(this.flightInstanceId, UUID.randomUUID())))
        .isInstanceOf(BusinessException.class).hasMessageContaining("cabin class");

    assertThat(seat.getHeldBy()).isNull();
  }

  @Test
  void hold_acceptsSeatsMatchingFlightAndCabinClass() {
    final UUID businessClass = UUID.randomUUID();
    final SeatInstance seat = seat();
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));
    when(this.cabinRepository.findAllById(any())).thenReturn(List.of(cabinOfClass(businessClass)));

    this.service.hold(this.alice, List.of(seat.getId()), new SeatHoldScope(this.flightInstanceId, businessClass));

    assertThat(seat.getHeldBy()).isEqualTo(this.alice);
  }

  @Test
  void hold_throwsNotFound_whenASeatDoesNotExist() {
    final UUID unknown = UUID.randomUUID();
    when(this.seatRepository.lockAllByIdIn(List.of(unknown))).thenReturn(List.of());

    assertThatThrownBy(() -> this.service.hold(this.alice, List.of(unknown)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void release_onlyClearsSeatsHeldByTheCaller() {
    final SeatInstance mine = seat();
    mine.setHeldBy(this.alice);
    mine.setHeldUntil(NOW.plusSeconds(60));
    final SeatInstance theirs = seat();
    theirs.setHeldBy(this.bob);
    theirs.setHeldUntil(NOW.plusSeconds(60));
    when(this.seatRepository.lockAllByIdIn(List.of(mine.getId(), theirs.getId()))).thenReturn(List.of(mine, theirs));

    this.service.release(this.alice, List.of(mine.getId(), theirs.getId()));

    assertThat(mine.getHeldBy()).isNull();
    assertThat(theirs.getHeldBy()).isEqualTo(this.bob);
  }

  @Test
  void confirmBooked_marksSeatBooked_clearsHold_andCountsItOnTheCabin() {
    final SeatInstance seat = seat();
    seat.setHeldBy(this.alice);
    seat.setHeldUntil(NOW.plusSeconds(60));
    final SeatInstance second = seat();
    second.setHeldBy(this.alice);
    second.setHeldUntil(NOW.plusSeconds(60));
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId(), second.getId()))).thenReturn(List.of(seat, second));

    this.service.confirmBooked(this.alice, List.of(seat.getId(), second.getId()));

    assertThat(seat.getStatus()).isEqualTo(SeatAvailabilityStatus.BOOKED);
    assertThat(seat.isBooked()).isTrue();
    assertThat(seat.isAvailable()).isFalse();
    assertThat(seat.getHeldBy()).isNull();
    // Một câu UPDATE nguyên tử cho cả cabin, không đọc-sửa-ghi entity.
    verify(this.cabinRepository).adjustBookedSeats(this.cabinId, 2, NOW);
    verify(this.cabinRepository, never()).saveAll(any());
  }

  @Test
  void confirmBooked_rejectsSeatNowHeldBySomeoneElse() {
    final SeatInstance seat = seat();
    seat.setHeldBy(this.bob);
    seat.setHeldUntil(NOW.plusSeconds(60));
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));

    assertThatThrownBy(() -> this.service.confirmBooked(this.alice, List.of(seat.getId())))
        .isInstanceOf(BusinessException.class);

    assertThat(seat.getStatus()).isEqualTo(SeatAvailabilityStatus.AVAILABLE);
  }

  @Test
  void free_returnsBookedSeatToAvailable_andDecrementsTheCabinCounter() {
    final SeatInstance seat = seat();
    seat.setStatus(SeatAvailabilityStatus.BOOKED);
    seat.setBooked(true);
    seat.setAvailable(false);
    final SeatInstance notBooked = seat();
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId(), notBooked.getId())))
        .thenReturn(List.of(seat, notBooked));

    this.service.free(List.of(seat.getId(), notBooked.getId()));

    assertThat(seat.getStatus()).isEqualTo(SeatAvailabilityStatus.AVAILABLE);
    assertThat(seat.isAvailable()).isTrue();
    // Chỉ ghế từng BOOKED mới trừ bộ đếm.
    verify(this.cabinRepository).adjustBookedSeats(this.cabinId, -1, NOW);
  }

  @Test
  void free_doesNotTouchTheCabinCounter_whenNoSeatWasBooked() {
    final SeatInstance seat = seat();
    when(this.seatRepository.lockAllByIdIn(List.of(seat.getId()))).thenReturn(List.of(seat));

    this.service.free(List.of(seat.getId()));

    verify(this.cabinRepository, never()).adjustBookedSeats(any(), org.mockito.ArgumentMatchers.anyInt(), any());
  }

  private com.nitditto.model.FlightInstanceCabin cabinOfClass(final UUID cabinClassId) {
    final com.nitditto.model.FlightInstanceCabin cabin = com.nitditto.model.FlightInstanceCabin.builder()
        .flightInstanceId(this.flightInstanceId).cabinClassId(cabinClassId).build();
    cabin.setId(this.cabinId);
    return cabin;
  }

  private SeatInstance seat() {
    final SeatInstance seat = SeatInstance.builder().flightInstanceId(this.flightInstanceId)
        .flightInstanceCabinId(this.cabinId).build();
    seat.setId(UUID.randomUUID());
    return seat;
  }
}
