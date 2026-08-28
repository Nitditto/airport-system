package com.nitditto.payload.request;

import com.nitditto.embeddable.Address;
import com.nitditto.embeddable.GeoCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirportRequest {

  @NotBlank(message = "IATA code is mandatory")
  @Size(min = 3, max = 3, message = "IATA code must be exactly 3 characters")
  private String iataCode;

  private String name;

  private ZoneId timeZone;

  @Valid
  private Address address;

  @NotNull(message = "City ID is mandatory")
  private UUID cityId;

  @Valid
  private GeoCode geoCode;
}
