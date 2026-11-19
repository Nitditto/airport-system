package com.nitditto.service.integration;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.payload.response.ApiResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Nạp hãng / máy bay / sân bay từ airline-core-service và location-service bằng endpoint batch-by-ids.
 *
 * <p>Số lời gọi HTTP là hằng số (tối đa 1 lời gọi cho mỗi loại, nhân số lô nếu vượt {@link #BATCH_SIZE}),
 * không phụ thuộc số bản ghi cần hiển thị (CLAUDE.md §7). Loại nào không có id thì không gọi.
 * Lỗi từ service khác được ném tiếp (FeignException) để GlobalExceptionHandler trả 502.
 */
@Component
@RequiredArgsConstructor
public class ReferenceDataLoader {

  /** Khớp giới hạn kích thước batch phía service đích. */
  static final int BATCH_SIZE = 200;

  private final AirlineClient airlineClient;
  private final LocationClient locationClient;

  public ReferenceData load(final Collection<UUID> airlineIds, final Collection<UUID> aircraftIds,
      final Collection<UUID> airportIds) {
    return new ReferenceData(
        fetch(airlineIds, this.airlineClient::getAirlinesByIds),
        fetch(aircraftIds, this.airlineClient::getAircraftsByIds),
        fetch(airportIds, this.locationClient::getAirportsByIds));
  }

  private static <T> Map<UUID, T> fetch(final Collection<UUID> ids,
      final Function<List<UUID>, ApiResponse<Map<UUID, T>>> batchCall) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    final List<UUID> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctIds.isEmpty()) {
      return Map.of();
    }

    final Map<UUID, T> result = new HashMap<>(distinctIds.size());
    for (int from = 0; from < distinctIds.size(); from += BATCH_SIZE) {
      final List<UUID> chunk = distinctIds.subList(from, Math.min(from + BATCH_SIZE, distinctIds.size()));
      final ApiResponse<Map<UUID, T>> response = batchCall.apply(chunk);
      if (response == null || response.getData() == null) {
        continue;
      }
      // Bỏ entry null: ReferenceData giữ map bất biến (Map.copyOf không nhận null).
      for (final Map.Entry<UUID, T> entry : response.getData().entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          result.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return result;
  }
}
