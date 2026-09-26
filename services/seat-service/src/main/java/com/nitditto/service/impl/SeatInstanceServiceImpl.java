package com.nitditto.service.impl;

import java.util.List;
import java.util.UUID;

import com.nitditto.model.SeatInstance;
import com.nitditto.repository.SeatInstanceRepository;
import com.nitditto.service.SeatInstanceService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatInstanceServiceImpl implements SeatInstanceService {

  private final SeatInstanceRepository seatInstanceRepository;

  @Override
  public Double calculateSeatPrice(final List<UUID> seatInstanceIds) {
    final List<SeatInstance> seatInstances = this.seatInstanceRepository.findAllById(seatInstanceIds);

    double price = 0;
    for (final SeatInstance si : seatInstances) {
      price += si.getPremiumSuperCharge() != null ? si.getPremiumSuperCharge() : 0;
    }

    return price;
  }

}
