package com.nitditto.payload.request;

import com.nitditto.enums.AirlineStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AirlineRequest {

  @NotBlank(message = "iata code is mandatory")
  @Size(min = 2, max = 2, message = "IATA code must be exactly 2 characters")
  private String iataCode;

  @NotBlank(message = "icao code is mandatory")
  @Size(min = 3, max = 3, message = "ICAO code must be exactly 3 characters")
  private String icaoCode;

  @NotBlank(message = "airline is mandatory")
  private String name;

  private String alias;

  private String logoUrl;

  private String website;

  private AirlineStatus status;

  private String alliance;

  private UUID headquartersCityId;

  private String supportEmail;
  private String supportPhone;
  private String supportHours;
}
