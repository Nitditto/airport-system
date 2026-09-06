package com.nitditto.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaggageMetadata {

  private Integer weight;
  private String unit;
  private Integer pieces;
  private String category;
  private String dimensions;
  private String notes;
}
