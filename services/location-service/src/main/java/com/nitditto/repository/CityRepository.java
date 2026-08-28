package com.nitditto.repository;

import com.nitditto.model.City;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CityRepository extends JpaRepository<City, UUID> {

  boolean existsByCityCode(String cityCode);

  boolean existsByCityCodeAndIdNot(String cityCode, UUID id);

  Page<City> findByCityCodeIgnoreCase(String cityCode, Pageable pageable);

  @Query("""
        select c from City c
        where lower(c.name) like lower(concat('%', :keyword, '%') )
        or lower(c.cityCode) like lower(concat('%', :keyword, '%' ))
        or lower(c.countryCode) like lower(concat('%', :keyword, '%' ))
        or lower(c.countryName) like lower(concat('%', :keyword, '%' ))
        or lower(c.regionCode) like lower(concat('%', :keyword, '%' ))
      """)
  Page<City> searchByKeyword(String keyword, Pageable pageable);
}
