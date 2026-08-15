package com.nitditto.controller;

import com.nitditto.payload.request.CityRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.CityResponse;
import com.nitditto.service.CityService;
import jakarta.validation.Valid;
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

  // /api/cities
  @PostMapping
  public ResponseEntity<CityResponse> createCity(@Valid @RequestBody CityRequest cityRequest)
      throws Exception {
    CityResponse cityResponse = cityService.createCity(cityRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(cityResponse);
  }

  // /api/cities/3
  @GetMapping("/{id}")
  public ResponseEntity<CityResponse> getCityById(@PathVariable Long id) throws Exception {
    CityResponse cityResponse = cityService.getCityById(id);
    return ResponseEntity.status(HttpStatus.OK).body(cityResponse);
  }

  // GET /cities?page=0&size=2&sortBy=name&sortDirection=asc
  @GetMapping
  public ResponseEntity<Page<CityResponse>> getAllCities(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "name") String sortBy,
      @RequestParam(defaultValue = "asc") String sortDirection
  ) throws Exception {

    Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
    Pageable pageable = PageRequest.of(page, size, sort);
    return ResponseEntity.ok(cityService.getAllCities(pageable));
  }

  @PutMapping("/{id}")
  public ResponseEntity<CityResponse> updateCity(@PathVariable Long id,
      @Valid @RequestBody CityRequest cityRequest) throws Exception {
    return ResponseEntity.ok(cityService.updateCity(id, cityRequest));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse> deleteCity(@PathVariable Long id) throws Exception {
    cityService.deleteCityById(id);
    return ResponseEntity.ok(new ApiResponse("City deleted successfully"));
  }

  @GetMapping("/search")
  public ResponseEntity<Page<CityResponse>> searchCities(
      @RequestParam String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size
  ) {
    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(cityService.searchCities(keyword, pageable));
  }

  @GetMapping("/country/{countryCode}")
  public ResponseEntity<Page<CityResponse>> getAllCitiesByCountryCode(
      @PathVariable String countryCode,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(
        cityService.getCitiesByCountryCode(countryCode.toUpperCase(), pageable));
  }

  @GetMapping("/exists/{cityCode}")
  public ResponseEntity<Boolean> checkCityExists(@PathVariable String cityCode) {
    return ResponseEntity.ok(cityService.cityExists(cityCode.toUpperCase()));
  }
}
