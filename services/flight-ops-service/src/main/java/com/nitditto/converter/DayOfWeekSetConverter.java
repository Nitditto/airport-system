package com.nitditto.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class DayOfWeekSetConverter implements AttributeConverter<Set<DayOfWeek>, String> {

  @Override
  public String convertToDatabaseColumn(final Set<DayOfWeek> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return "";
    }
    return attribute.stream().map(Enum::name).collect(Collectors.joining(","));
  }

  @Override
  public Set<DayOfWeek> convertToEntityAttribute(final String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return new HashSet<>();
    }
    return Arrays.stream(dbData.split(",")).map(DayOfWeek::valueOf)
        .collect(Collectors.toCollection(HashSet::new));
  }
}
