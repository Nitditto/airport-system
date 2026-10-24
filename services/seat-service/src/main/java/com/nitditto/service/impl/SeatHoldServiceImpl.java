package com.nitditto.service.impl;

import com.nitditto.enums.SeatAvailabilityStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.FlightInstanceCabin;
import com.nitditto.model.SeatInstance;
import com.nitditto.payload.response.SeatHoldResponse;
import com.nitditto.repository.FlightInstanceCabinRepository;
import com.nitditto.repository.SeatInstanceRepository;
import com.nitditto.service.SeatHoldPolicy;
import com.nitditto.service.SeatHoldService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giữ chỗ ghế bằng cột heldBy/heldUntil trên chính hàng SeatInstance thay vì Redis: cùng transaction + khoá hàng
 * với dữ liệu ghế nên không có hai nguồn sự thật, không thêm hạ tầng, và hết hạn được xử lý lúc đọc/ghi.
 */
@Service
@Transactional(readOnly = true)
public class SeatHoldServiceImpl implements SeatHoldService {

  private final SeatInstanceRepository seatInstanceRepository;
  private final FlightInstanceCabinRepository flightInstanceCabinRepository;
  private final Clock clock;
  private final Duration holdTtl;

  public SeatHoldServiceImpl(final SeatInstanceRepository seatInstanceRepository,
      final FlightInstanceCabinRepository flightInstanceCabinRepository, final Clock clock,
      @Value("${seat.hold.ttl-minutes:15}") final long ttlMinutes) {
    this.seatInstanceRepository = seatInstanceRepository;
    this.flightInstanceCabinRepository = flightInstanceCabinRepository;
    this.clock = clock;
    this.holdTtl = Duration.ofMinutes(ttlMinutes);
  }

  @Override
  @Transactional
  public SeatHoldResponse hold(final UUID userId, final List<UUID> seatInstanceIds) {
    final List<UUID> ids = seatInstanceIds.stream().distinct().toList();
    final Instant now = this.clock.instant();
    final List<SeatInstance> seats = this.lockAll(ids);

    final List<UUID> unavailable = seats.stream()
        .filter(seat -> !SeatHoldPolicy.isOpenFor(seat, userId, now)).map(SeatInstance::getId).toList();
    if (!unavailable.isEmpty()) {
      throw new BusinessException("Seat(s) no longer available: " + unavailable);
    }

    // Người dùng đổi ghế: bỏ các ghế họ đang giữ trên cùng chuyến mà không còn nằm trong lựa chọn mới.
    final Set<UUID> flightInstanceIds = seats.stream().map(SeatInstance::getFlightInstanceId)
        .collect(Collectors.toSet());
    final Set<UUID> keep = new HashSet<>(ids);
    final List<SeatInstance> stale = this.seatInstanceRepository
        .findByFlightInstanceIdInAndHeldBy(flightInstanceIds, userId).stream()
        .filter(seat -> !keep.contains(seat.getId())).toList();
    stale.forEach(this::clearHold);

    final Instant expiresAt = now.plus(this.holdTtl);
    seats.forEach(seat -> {
      seat.setHeldBy(userId);
      seat.setHeldUntil(expiresAt);
    });
    this.seatInstanceRepository.saveAll(stale);
    this.seatInstanceRepository.saveAll(seats);
    return SeatHoldResponse.builder().seatInstanceIds(ids).expiresAt(expiresAt).build();
  }

  @Override
  @Transactional
  public void release(final UUID userId, final List<UUID> seatInstanceIds) {
    final List<SeatInstance> mine = this.lockAll(seatInstanceIds.stream().distinct().toList()).stream()
        .filter(seat -> userId.equals(seat.getHeldBy())).toList();
    mine.forEach(this::clearHold);
    this.seatInstanceRepository.saveAll(mine);
  }

  @Override
  @Transactional
  public void confirmBooked(final UUID userId, final List<UUID> seatInstanceIds) {
    final Instant now = this.clock.instant();
    final List<SeatInstance> seats = this.lockAll(seatInstanceIds.stream().distinct().toList());

    final List<UUID> unavailable = seats.stream()
        .filter(seat -> !SeatHoldPolicy.isOpenFor(seat, userId, now)).map(SeatInstance::getId).toList();
    if (!unavailable.isEmpty()) {
      throw new BusinessException("Seat(s) can no longer be confirmed: " + unavailable);
    }

    seats.forEach(seat -> {
      seat.setStatus(SeatAvailabilityStatus.BOOKED);
      seat.setBooked(true);
      seat.setAvailable(false);
      this.clearHold(seat);
    });
    this.seatInstanceRepository.saveAll(seats);
    this.adjustBookedCounters(seats, 1);
  }

  @Override
  @Transactional
  public void free(final List<UUID> seatInstanceIds) {
    final List<SeatInstance> seats = this.lockAll(seatInstanceIds.stream().distinct().toList());
    final List<SeatInstance> wasBooked = seats.stream()
        .filter(seat -> seat.getStatus() == SeatAvailabilityStatus.BOOKED).toList();
    seats.forEach(seat -> {
      seat.setStatus(SeatAvailabilityStatus.AVAILABLE);
      seat.setBooked(false);
      seat.setAvailable(true);
      this.clearHold(seat);
    });
    this.seatInstanceRepository.saveAll(seats);
    this.adjustBookedCounters(wasBooked, -1);
  }

  private List<SeatInstance> lockAll(final Collection<UUID> ids) {
    final List<SeatInstance> seats = this.seatInstanceRepository.lockAllByIdIn(ids);
    if (seats.size() != ids.size()) {
      final Set<UUID> found = seats.stream().map(SeatInstance::getId).collect(Collectors.toSet());
      final UUID missing = ids.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
      throw new ResourceNotFoundException("SeatInstance", "id", missing);
    }
    return seats;
  }

  private void clearHold(final SeatInstance seat) {
    seat.setHeldBy(null);
    seat.setHeldUntil(null);
  }

  // Giữ số ghế đã bán trên FlightInstanceCabin khớp với trạng thái từng ghế (availableSeats = total - booked).
  private void adjustBookedCounters(final List<SeatInstance> seats, final int delta) {
    if (seats.isEmpty()) {
      return;
    }
    final Map<UUID, Long> countByCabin = seats.stream()
        .collect(Collectors.groupingBy(SeatInstance::getFlightInstanceCabinId, Collectors.counting()));
    final Map<UUID, FlightInstanceCabin> cabinById = this.flightInstanceCabinRepository
        .findAllById(countByCabin.keySet()).stream()
        .collect(Collectors.toMap(FlightInstanceCabin::getId, Function.identity()));
    countByCabin.forEach((cabinId, count) -> {
      final FlightInstanceCabin cabin = cabinById.get(cabinId);
      if (cabin != null) {
        final int current = cabin.getBookedSeats() != null ? cabin.getBookedSeats() : 0;
        cabin.setBookedSeats(Math.max(0, current + delta * count.intValue()));
      }
    });
    this.flightInstanceCabinRepository.saveAll(cabinById.values());
  }
}
