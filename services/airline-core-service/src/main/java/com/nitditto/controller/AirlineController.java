package com.nitditto.controller;

import com.nitditto.enums.AirlineStatus;
import com.nitditto.payload.request.AirlineRequest;
import com.nitditto.payload.response.AirlineDropDownItem;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.service.AirlineService;
import jakarta.validation.Valid;
import java.util.List;
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
  public ResponseEntity<AirlineResponse> createAirline(
      @Valid @RequestBody AirlineRequest airlineRequest, @RequestHeader("X-User-Id") Long userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(airlineService.createAirline(airlineRequest, userId));
  }

  @GetMapping("/admin")
  public ResponseEntity<AirlineResponse> getAirlineByOwner(
      @RequestHeader("X-User-Id") Long userId) throws Exception {
    return ResponseEntity.ok(airlineService.getAirlineByOwner(userId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<AirlineResponse> getAirlineById(
      @PathVariable Long id) throws Exception {
    return ResponseEntity.ok(airlineService.getAirlineById(id));
  }

  @GetMapping
  public ResponseEntity<Page<AirlineResponse>> getAllAirlines(Pageable pageable) {
    return ResponseEntity.ok(airlineService.getAllAirlines(pageable));
  }

  @GetMapping("/dropdown")
  public ResponseEntity<List<AirlineDropDownItem>> getAirlinesForDropDown() {
    return ResponseEntity.ok(airlineService.getAirlineDropDown());
  }

  @PutMapping
  public ResponseEntity<AirlineResponse> updateAirline(
      @Valid @RequestBody AirlineRequest airlineRequest, @RequestHeader("X-User-Id") Long userId)
      throws Exception {
    return ResponseEntity.ok(airlineService.updateAirline(airlineRequest, userId));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<String>> deleteAirline(@PathVariable Long id,
      @RequestHeader("X-User-Id") Long userId) throws Exception {
    airlineService.deleteAirline(id, userId);
    return ResponseEntity.ok(ApiResponse.success("airline deleted successfully"));
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<AirlineResponse> approveAirline(@PathVariable Long id) throws Exception {
    return ResponseEntity.ok(airlineService.changeStatusByAdmin(id, AirlineStatus.ACTIVE));
  }

  @PostMapping("/{id}/suspend")
  public ResponseEntity<AirlineResponse> suspendAirline(@PathVariable Long id) throws Exception {
    return ResponseEntity.ok(airlineService.changeStatusByAdmin(id, AirlineStatus.INACTIVE));
  }

  @PostMapping("/{id}/ban")
  public ResponseEntity<AirlineResponse> banAirline(@PathVariable Long id) throws Exception {
    return ResponseEntity.ok(airlineService.changeStatusByAdmin(id, AirlineStatus.BANNED));
  }
}
