package com.nitditto.controller;

import com.nitditto.payload.request.CityRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.CityResponse;
import com.nitditto.service.CityService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cities")
public class LocationController {

  private final CityService cityService;

  @PostMapping
  public ResponseEntity<ApiResponse<CityResponse>> createCity(@Valid @RequestBody final CityRequest cityRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.cityService.createCity(cityRequest)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<CityResponse>> getCityById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.cityService.getCityById(id)));
  }

  // GET /cities?page=0&size=2&sortBy=name&sortDirection=asc
  @GetMapping
  public ResponseEntity<ApiResponse<Page<CityResponse>>> getAllCities(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(defaultValue = "name") final String sortBy,
      @RequestParam(defaultValue = "asc") final String sortDirection
  ) {
    final Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
    final Pageable pageable = PageRequest.of(page, size, sort);
    return ResponseEntity.ok(ApiResponse.success(this.cityService.getAllCities(pageable)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<CityResponse>> updateCity(@PathVariable final UUID id,
      @Valid @RequestBody final CityRequest cityRequest) {
    return ResponseEntity.ok(ApiResponse.success(this.cityService.updateCity(id, cityRequest)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteCity(@PathVariable final UUID id) {
    this.cityService.deleteCityById(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/search")
  public ResponseEntity<ApiResponse<Page<CityResponse>>> searchCities(
      @RequestParam final String keyword,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size
  ) {
    final Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(ApiResponse.success(this.cityService.searchCities(keyword, pageable)));
  }

  @GetMapping("/country/{countryCode}")
  public ResponseEntity<ApiResponse<Page<CityResponse>>> getAllCitiesByCountryCode(
      @PathVariable final String countryCode,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size
  ) {
    final Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(
        ApiResponse.success(this.cityService.getCitiesByCountryCode(countryCode.toUpperCase(), pageable)));
  }

  @GetMapping("/exists/{cityCode}")
  public ResponseEntity<ApiResponse<Boolean>> checkCityExists(@PathVariable final String cityCode) {
    return ResponseEntity.ok(ApiResponse.success(this.cityService.cityExists(cityCode.toUpperCase())));
  }
}
