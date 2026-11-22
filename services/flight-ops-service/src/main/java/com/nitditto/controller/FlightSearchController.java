package com.nitditto.controller;

import com.nitditto.payload.request.FlightSearchRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.service.FlightSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightSearchController {

  private final FlightSearchService flightSearchService;

  // Công khai (gateway PUBLIC_READS). Literal /search được Spring ưu tiên hơn /{id} của FlightController.
  // Sắp xếp do query param sortBy/sortOrder quyết định, không dùng Pageable sort.
  @GetMapping("/search")
  public ResponseEntity<ApiResponse<Page<FlightInstanceResponse>>> searchFlights(
      @Valid final FlightSearchRequest request, final Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(this.flightSearchService.searchFlights(request, pageable)));
  }
}
