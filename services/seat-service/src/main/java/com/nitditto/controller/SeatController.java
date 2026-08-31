package com.nitditto.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nitditto.payload.request.SeatRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.SeatResponse;
import com.nitditto.service.SeatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /**
     * Ghế bình thường đã được sinh tự động khi tạo SeatMap (SeatMapServiceImpl.createSeatMap).
     * Endpoint này là đường sửa chữa cho seat map chưa có ghế; gọi lại khi đã có ghế sẽ bị
     * SeatServiceImpl.generateSeats chặn bằng BusinessException -> 400.
     */
    @PostMapping("/seat-map/{seatMapId}/generate")
    public ResponseEntity<Void> generateSeats(@PathVariable("seatMapId") final UUID seatMapId) {
        this.seatService.generateSeats(seatMapId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/seat-map/{seatMapId}")
    public ResponseEntity<ApiResponse<Page<SeatResponse>>> getSeatsBySeatMapId(
            @PathVariable("seatMapId") final UUID seatMapId, final Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(this.seatService.getSeatsBySeatMapId(seatMapId, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SeatResponse>> getSeatById(@PathVariable("id") final UUID seatId) {
        return ResponseEntity.ok(ApiResponse.success(this.seatService.getSeatById(seatId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SeatResponse>> updateSeat(
            @PathVariable("id") final UUID seatId,
            @Valid @RequestBody final SeatRequest seatRequest) {
        return ResponseEntity.ok(ApiResponse.success(this.seatService.updateSeat(seatId, seatRequest)));
    }

}
