package com.nitditto.payload.response;

import com.nitditto.embeddable.Support;
import com.nitditto.enums.AirlineStatus;
import com.nitditto.payload.dto.UserDTO;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirlineResponse {

  private Long id;

  private String iataCode;
  private String icaoCode;

  private String name;
  private String alias;

  private String logoUrl;
  private String website;
  private AirlineStatus status;
  private String alliance;
  private Instant createdAt;
  private Instant updatedAt;

  private Long ownerId;
  private UserDTO owner;
  private Long updatedById;

  private CityResponse headquartersCity;
  private Support support;
}
