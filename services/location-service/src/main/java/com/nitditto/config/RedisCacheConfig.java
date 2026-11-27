package com.nitditto.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Cache-aside cho dữ liệu tham chiếu gần như tĩnh (Airport/City). Redis chết thì đọc thẳng DB, không trả 500.
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

  public static final String AIRPORTS = "airports";
  public static final String AIRPORT = "airport";
  public static final String AIRPORTS_BY_CITY = "airportsByCity";
  public static final String CITY = "city";

  // shortcut: TTL cố định 12h cho mọi cache, tách theo từng cache khi có dữ liệu cần tươi hơn
  public static final Duration TTL = Duration.ofHours(12);

  @Bean
  public GenericJacksonJsonRedisSerializer locationRedisSerializer() {
    // Chỉ cho giải mã kiểu của chính project + JDK, không để payload Redis tự chọn class tuỳ ý.
    return GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.nitditto.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .build())
        .build();
  }

  @Bean
  public RedisCacheConfiguration locationCacheConfiguration(final GenericJacksonJsonRedisSerializer serializer) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(TTL)
        .prefixCacheNameWith("location:")
        .disableCachingNullValues()
        .serializeKeysWith(SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
        .serializeValuesWith(SerializationPair.fromSerializer(serializer));
  }

  @Bean
  public RedisCacheManager cacheManager(final RedisConnectionFactory connectionFactory,
      final RedisCacheConfiguration locationCacheConfiguration) {
    return RedisCacheManager.builder(connectionFactory).cacheDefaults(locationCacheConfiguration).build();
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      @Override
      public void handleCacheGetError(final RuntimeException ex, final Cache cache, final Object key) {
        log.warn("Cache get failed cache={} key={}: {}", cache.getName(), key, ex.toString());
      }

      @Override
      public void handleCachePutError(final RuntimeException ex, final Cache cache, final Object key,
          final Object value) {
        log.warn("Cache put failed cache={} key={}: {}", cache.getName(), key, ex.toString());
      }

      @Override
      public void handleCacheEvictError(final RuntimeException ex, final Cache cache, final Object key) {
        // Evict hỏng = có thể còn dữ liệu cũ tới khi hết TTL; log error để thấy được.
        log.error("Cache evict failed cache={} key={}: {}", cache.getName(), key, ex.toString());
      }

      @Override
      public void handleCacheClearError(final RuntimeException ex, final Cache cache) {
        log.error("Cache clear failed cache={}: {}", cache.getName(), ex.toString());
      }
    };
  }
}
