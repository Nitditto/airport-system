package com.nitditto.controller;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.service.SeatInstanceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seat-instances")
@RequiredArgsConstructor
public class SeatInstanceController {

  private final SeatInstanceService seatInstanceService;

  @PostMapping("/price/price")
  public Double calculateSeatPrice(@RequestBody List<UUID> seatInstanceIds) {
    return this.seatInstanceService.calculateSeatPrice(seatInstanceIds);
  }
}
