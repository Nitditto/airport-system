package com.nitditto.service;

import com.nitditto.domain.AncillaryMetadata;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import tools.jackson.databind.ObjectMapper;

@Convert
public class AncillaryMetadataConverter implements AttributeConverter<AncillaryMetadata, String> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(AncillaryMetadata ancillaryMetadata) {

    if (ancillaryMetadata == null)
      return null;
    return this.objectMapper.writeValueAsString(ancillaryMetadata);
  }

  @Override
  public AncillaryMetadata convertToEntityAttribute(String s) {

    if (s == null)
      return null;
    return this.objectMapper.readValue(s, AncillaryMetadata.class);
  }
}
