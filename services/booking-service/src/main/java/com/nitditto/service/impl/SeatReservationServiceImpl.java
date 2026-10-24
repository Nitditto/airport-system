package com.nitditto.service.impl;

import com.nitditto.client.SeatClient;
import com.nitditto.exception.BusinessException;
import com.nitditto.payload.request.SeatHoldRequest;
import com.nitditto.service.SeatReservationService;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatReservationServiceImpl implements SeatReservationService {

  private final SeatClient seatClient;

  @Override
  public void hold(final UUID userId, final List<UUID> seatInstanceIds) {
    try {
      this.seatClient.hold(userId, request(seatInstanceIds));
    } catch (final FeignException.BadRequest ex) {
      throw new BusinessException("One or more selected seats are no longer available");
    }
  }

  @Override
  public void releaseQuietly(final UUID userId, final List<UUID> seatInstanceIds) {
    try {
      this.seatClient.release(userId, request(seatInstanceIds));
    } catch (final RuntimeException ex) {
      log.warn("Could not release held seats {} for user {}: {}", seatInstanceIds, userId, ex.getMessage());
    }
  }

  @Override
  public void confirmBooked(final UUID userId, final List<UUID> seatInstanceIds) {
    try {
      this.seatClient.confirmBooked(userId, request(seatInstanceIds));
    } catch (final FeignException.BadRequest ex) {
      throw new BusinessException("Seats can no longer be confirmed for this booking");
    }
  }

  @Override
  public void freeQuietly(final List<UUID> seatInstanceIds) {
    try {
      this.seatClient.free(request(seatInstanceIds));
    } catch (final RuntimeException ex) {
      log.warn("Could not free seats {}: {}", seatInstanceIds, ex.getMessage());
    }
  }

  private static SeatHoldRequest request(final List<UUID> seatInstanceIds) {
    return SeatHoldRequest.builder().seatInstanceIds(seatInstanceIds).build();
  }
}
