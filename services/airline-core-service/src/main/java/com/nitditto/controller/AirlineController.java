package com.nitditto.controller;

import com.nitditto.enums.AirlineStatus;
import com.nitditto.payload.request.AirlineRequest;
import com.nitditto.payload.response.AirlineDropDownItem;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.service.AirlineService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/airlines")
@RestController
@RequiredArgsConstructor
public class AirlineController {

  private final AirlineService airlineService;

  @PostMapping
  public ResponseEntity<ApiResponse<AirlineResponse>> createAirline(
      @Valid @RequestBody final AirlineRequest airlineRequest,
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.airlineService.createAirline(airlineRequest, userId)));
  }

  @GetMapping("/admin")
  public ResponseEntity<ApiResponse<AirlineResponse>> getAirlineByOwner(
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.getAirlineByOwner(userId)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AirlineResponse>> getAirlineById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.getAirlineById(id)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<AirlineResponse>>> getAllAirlines(final Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.getAllAirlines(pageable)));
  }

  @GetMapping("/dropdown")
  public ResponseEntity<ApiResponse<List<AirlineDropDownItem>>> getAirlinesForDropDown() {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.getAirlineDropDown()));
  }

  @PutMapping
  public ResponseEntity<ApiResponse<AirlineResponse>> updateAirline(
      @Valid @RequestBody final AirlineRequest airlineRequest,
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.updateAirline(airlineRequest, userId)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteAirline(@PathVariable final UUID id,
      @RequestHeader("X-User-Id") final UUID userId) {
    this.airlineService.deleteAirline(id, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<ApiResponse<AirlineResponse>> approveAirline(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.changeStatusByAdmin(id, AirlineStatus.ACTIVE)));
  }

  @PostMapping("/{id}/suspend")
  public ResponseEntity<ApiResponse<AirlineResponse>> suspendAirline(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.changeStatusByAdmin(id, AirlineStatus.INACTIVE)));
  }

  @PostMapping("/{id}/ban")
  public ResponseEntity<ApiResponse<AirlineResponse>> banAirline(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.airlineService.changeStatusByAdmin(id, AirlineStatus.BANNED)));
  }
}
