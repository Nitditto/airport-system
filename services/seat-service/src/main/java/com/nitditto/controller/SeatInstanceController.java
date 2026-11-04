package com.nitditto.controller;

import com.nitditto.exception.BusinessException;
import com.nitditto.payload.request.SeatHoldRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.SeatHoldResponse;
import com.nitditto.payload.response.SeatInstanceResponse;
import com.nitditto.service.SeatHoldService;
import com.nitditto.service.SeatInstanceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seat-instances")
@RequiredArgsConstructor
public class SeatInstanceController {

  private static final int MAX_BATCH_SIZE = 200;

  private final SeatInstanceService seatInstanceService;
  private final SeatHoldService seatHoldService;

  @PostMapping("/price/price")
  public Double calculateSeatPrice(@RequestBody List<UUID> seatInstanceIds) {
    return this.seatInstanceService.calculateSeatPrice(seatInstanceIds);
  }

  @PostMapping("/batch-by-ids")
  public ResponseEntity<ApiResponse<Map<UUID, SeatInstanceResponse>>> getSeatInstancesByIds(
      @RequestBody final List<UUID> seatInstanceIds) {
    if (seatInstanceIds.size() > MAX_BATCH_SIZE) {
      throw new BusinessException("Cannot load more than %d seat instances at once".formatted(MAX_BATCH_SIZE));
    }
    return ResponseEntity.ok(ApiResponse.success(this.seatInstanceService.getSeatInstancesByIds(seatInstanceIds)));
  }

  // Khách hàng (mọi người dùng đăng nhập) gọi qua gateway: giữ chỗ tạm thời trong lúc điền thông tin/thanh toán.
  @PostMapping("/hold")
  public ResponseEntity<ApiResponse<SeatHoldResponse>> hold(
      @RequestHeader("X-User-Id") final UUID userId, @Valid @RequestBody final SeatHoldRequest request) {
    return ResponseEntity.ok(ApiResponse.success(this.seatHoldService.hold(userId, request.getSeatInstanceIds())));
  }

  @PostMapping("/release")
  public ResponseEntity<ApiResponse<Void>> release(
      @RequestHeader("X-User-Id") final UUID userId, @Valid @RequestBody final SeatHoldRequest request) {
    this.seatHoldService.release(userId, request.getSeatInstanceIds());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  // Nội bộ (booking-service qua Feign, không qua gateway): gateway chỉ cho ADMIN/OWNER ghi vào route seat-service,
  // nên khách hàng không gọi được hai endpoint dưới đây từ bên ngoài.
  @PostMapping("/confirm-booked")
  public ResponseEntity<ApiResponse<Void>> confirmBooked(
      @RequestHeader("X-User-Id") final UUID userId, @Valid @RequestBody final SeatHoldRequest request) {
    this.seatHoldService.confirmBooked(userId, request.getSeatInstanceIds());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PostMapping("/free")
  public ResponseEntity<ApiResponse<Void>> free(@Valid @RequestBody final SeatHoldRequest request) {
    this.seatHoldService.free(request.getSeatInstanceIds());
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
