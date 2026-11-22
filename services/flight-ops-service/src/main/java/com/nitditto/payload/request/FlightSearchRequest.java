package com.nitditto.payload.request;

import com.nitditto.enums.CabinClassType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** Tham số tìm chuyến bay (bind từ query string). Chỉ flight-ops dùng nên không đặt ở common-lib. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightSearchRequest {

  @NotNull(message = "Departure airport is required")
  private UUID departureAirportId;

  @NotNull(message = "Arrival airport is required")
  private UUID arrivalAirportId;

  @NotNull(message = "Departure date is required")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate departureDate;

  @NotNull(message = "Passengers is required")
  @Min(value = 1, message = "At least 1 passenger is required")
  @Max(value = 9, message = "At most 9 passengers are allowed")
  private Integer passengers;

  @NotNull(message = "Cabin class is required")
  private CabinClassType cabinClass;

  // Bộ lọc tuỳ chọn. Khoảng giờ: any | morning | afternoon | evening | night.
  private List<UUID> airlines;
  @PositiveOrZero(message = "minPrice must not be negative")
  private Double minPrice;
  @PositiveOrZero(message = "maxPrice must not be negative")
  private Double maxPrice;
  private String departureTimeRange;
  private String arrivalTimeRange;
  @Min(value = 1, message = "maxDuration must be positive")
  private Integer maxDuration;
  private String alliance;

  // Sắp xếp: sortBy = price | duration | departure | arrival; sortOrder = asc | desc.
  private String sortBy;
  private String sortOrder;
}
