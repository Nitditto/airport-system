package com.nitditto.payload.request;

import com.nitditto.domain.AncillaryMetadata;
import com.nitditto.enums.AncillaryType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AncillaryRequest {

  @NotNull(message = "Ancillary Type is required")
  private AncillaryType type;

  @Size(max = 100, message = "Sub-type cannot be longer than 100 characters")
  private String subType;

  @Size(max = 10, message = "RFISC cannot be longer than 10 characters")
  private String rfisc;

  @NotBlank(message = "Name is required")
  @Size(max = 200, message = "Name cannot be longer than 200 characters")
  private String name;

  @Size(max = 1000, message = "Description cannot be longer than 1000 characters")
  private String description;

  @Size(max = 500, message = "Icon URL cannot be longer than 500 characters")
  private String iconUrl;
  private AncillaryMetadata metadata;

  private Integer displayOrder;

}
