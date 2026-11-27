package com.nitditto.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.cache.annotation.CacheEvict;

/**
 * Gắn lên mọi method ghi Airport/City. Xoá sạch vì dữ liệu ghi hiếm, và AirportResponse nhúng CityResponse
 * nên sửa City cũng làm cache Airport cũ.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@CacheEvict(cacheNames = {RedisCacheConfig.AIRPORTS, RedisCacheConfig.AIRPORT,
    RedisCacheConfig.AIRPORTS_BY_CITY, RedisCacheConfig.CITY}, allEntries = true)
public @interface EvictLocationCaches {
}
