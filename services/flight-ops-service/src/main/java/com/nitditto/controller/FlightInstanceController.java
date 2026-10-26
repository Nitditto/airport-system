package com.nitditto.controller;

import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.service.FlightInstanceService;
import jakarta.validation.Valid;
import com.nitditto.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flight-instances")
public class FlightInstanceController {

  private static final int MAX_BATCH_SIZE = 200;

  private final FlightInstanceService flightInstanceService;

  @PostMapping
  public ResponseEntity<ApiResponse<FlightInstanceResponse>> createFlightInstance(
      @RequestHeader("X-User-Id") final UUID userId,
      @Valid @RequestBody final FlightInstanceRequest flightInstanceRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            this.flightInstanceService.createFlightInstance(userId, flightInstanceRequest)));
  }

  // Công khai (gateway cho qua không cần JWT): khai báo trước /{id} để không bị coi là id.
  @GetMapping("/search")
  public ResponseEntity<ApiResponse<Page<FlightInstanceResponse>>> search(
      @RequestParam final UUID departureAirportId,
      @RequestParam final UUID arrivalAirportId,
      @RequestParam final LocalDate onDate,
      @RequestParam(defaultValue = "1") final int passengers,
      @PageableDefault(size = 50, sort = "departureDateTime") final Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(this.flightInstanceService.searchPublic(
        departureAirportId, arrivalAirportId, onDate, passengers, pageable)));
  }

  @PostMapping("/batch-by-ids")
  public ResponseEntity<ApiResponse<Map<UUID, FlightInstanceResponse>>> getByIds(
      @RequestBody final List<UUID> ids) {
    if (ids.size() > MAX_BATCH_SIZE) {
      throw new BusinessException("Cannot load more than %d flight instances at once".formatted(MAX_BATCH_SIZE));
    }
    return ResponseEntity.ok(ApiResponse.success(this.flightInstanceService.getByIds(ids)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightInstanceResponse>> getFlightInstance(
      @PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.flightInstanceService.getFlightInstanceById(id)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<FlightInstanceResponse>>> getByAirlineId(
      @RequestHeader("X-User-Id") final UUID userId,
      @RequestParam(required = false) final UUID departureAirportId,
      @RequestParam(required = false) final UUID arrivalAirportId,
      @RequestParam(required = false) final UUID flightId,
      @RequestParam(required = false) final LocalDate onDate, final Pageable pageable
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        this.flightInstanceService.getByAirlineId(userId, departureAirportId, arrivalAirportId,
            flightId, onDate, pageable)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightInstanceResponse>> updateFlightInstance(
      @PathVariable final UUID id,
      @Valid @RequestBody final FlightInstanceRequest flightInstanceRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightInstanceService.updateFlightInstance(id, flightInstanceRequest)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFlightInstance(@PathVariable final UUID id) {
    this.flightInstanceService.deleteFlightInstance(id);
    return ResponseEntity.noContent().build();
  }
}
