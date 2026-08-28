package com.nitditto.payload.response;

import com.nitditto.embeddable.Address;
import com.nitditto.embeddable.GeoCode;
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
public class AirportResponse {

  private UUID id;
  private String iataCode;
  private String name;
  private String detailedName;
  private ZoneId timeZone;
  private Address address;
  private CityResponse city;
  private GeoCode geoCode;

}
