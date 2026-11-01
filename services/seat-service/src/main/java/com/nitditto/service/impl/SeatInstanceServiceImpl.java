package com.nitditto.service.impl;

import com.nitditto.mapper.SeatInstanceMapper;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatInstance;
import com.nitditto.payload.response.SeatInstanceResponse;
import com.nitditto.repository.SeatInstanceRepository;
import com.nitditto.repository.SeatRepository;
import com.nitditto.service.SeatInstanceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatInstanceServiceImpl implements SeatInstanceService {

  private final SeatInstanceRepository seatInstanceRepository;
  private final SeatRepository seatRepository;

  @Override
  public Double calculateSeatPrice(final List<UUID> seatInstanceIds) {
    final List<SeatInstance> seatInstances = this.seatInstanceRepository.findAllById(seatInstanceIds);

    double price = 0;
    for (final SeatInstance si : seatInstances) {
      price += si.getPremiumSuperCharge() != null ? si.getPremiumSuperCharge() : 0;
    }

    return price;
  }

  @Override
  public Map<UUID, SeatInstanceResponse> getSeatInstancesByIds(final List<UUID> seatInstanceIds) {
    if (seatInstanceIds == null || seatInstanceIds.isEmpty()) {
      return Map.of();
    }
    final List<SeatInstance> seatInstances = this.seatInstanceRepository.findAllById(seatInstanceIds);
    final Map<UUID, Seat> seatById = this.seatRepository
        .findAllById(seatInstances.stream().map(SeatInstance::getSeatId).distinct().toList()).stream()
        .collect(Collectors.toMap(Seat::getId, Function.identity()));
    return seatInstances.stream().collect(Collectors.toMap(SeatInstance::getId,
        si -> SeatInstanceMapper.toResponse(
            new SeatInstanceMapper.SeatInstanceContext(si, seatById.get(si.getSeatId()), null, null))));
  }
}
