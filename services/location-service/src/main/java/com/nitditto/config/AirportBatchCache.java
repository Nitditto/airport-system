package com.nitditto.config;

import com.nitditto.payload.response.AirportResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.stereotype.Component;

/**
 * Đọc/ghi nhiều Airport vào cache "airport" bằng một MGET và một pipeline, thay vì N lượt Redis. Cùng key và
 * serializer với @Cacheable getAirportById nên hai đường dùng chung dữ liệu và cùng bị @EvictLocationCaches xoá.
 * Redis lỗi thì coi như miss, không làm hỏng request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirportBatchCache {

  private final RedisConnectionFactory connectionFactory;
  private final RedisCacheConfiguration cacheConfiguration;
  private final GenericJacksonJsonRedisSerializer serializer;

  public Map<UUID, AirportResponse> getAll(final List<UUID> ids) {
    final Map<UUID, AirportResponse> found = new HashMap<>(ids.size());
    if (ids.isEmpty()) {
      return found;
    }
    final byte[][] keys = ids.stream().map(this::key).toArray(byte[][]::new);
    try (RedisConnection connection = this.connectionFactory.getConnection()) {
      final List<byte[]> values = connection.stringCommands().mGet(keys);
      for (int i = 0; values != null && i < values.size(); i++) {
        if (values.get(i) != null) {
          found.put(ids.get(i), this.serializer.deserialize(values.get(i), AirportResponse.class));
        }
      }
    } catch (final RuntimeException ex) {
      log.warn("Airport batch cache read failed: {}", ex.toString());
      found.clear();
    }
    return found;
  }

  public void putAll(final Collection<AirportResponse> responses) {
    if (responses.isEmpty()) {
      return;
    }
    try (RedisConnection connection = this.connectionFactory.getConnection()) {
      connection.openPipeline();
      for (final AirportResponse response : responses) {
        connection.stringCommands().set(this.key(response.getId()), this.serializer.serialize(response),
            Expiration.from(RedisCacheConfig.TTL), SetOption.UPSERT);
      }
      connection.closePipeline();
    } catch (final RuntimeException ex) {
      log.warn("Airport batch cache write failed: {}", ex.toString());
    }
  }

  private byte[] key(final UUID id) {
    return (this.cacheConfiguration.getKeyPrefixFor(RedisCacheConfig.AIRPORT) + id).getBytes(StandardCharsets.UTF_8);
  }
}
