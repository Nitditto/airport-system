package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirlineDropDownItem {

  private UUID id;
  private String name;
  private String iataCode;
  private String icaoCode;
  private String logoUrl;
  private String country;
}
