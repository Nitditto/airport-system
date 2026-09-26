package com.nitditto.service;

import java.util.List;
import java.util.UUID;

public interface SeatInstanceService {

  Double calculateSeatPrice(List<UUID> seatInstanceIds);
}
