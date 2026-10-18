package com.nitditto.repository;

import com.nitditto.enums.AncillaryType;
import com.nitditto.model.FlightCabinAncillary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FlightCabinAncillaryRepository extends JpaRepository<FlightCabinAncillary, UUID> {

  List<FlightCabinAncillary> findByFlightIdAndCabinClassId(UUID flightId, UUID cabinClassId);

  boolean existsByAncillaryId(UUID ancillaryId);

  boolean existsByFlightIdAndCabinClassIdAndAncillaryId(UUID flightId, UUID cabinClassId, UUID ancillaryId);

  // Không có association tới Ancillary (rule NO JPA relationship), nên lọc theo type bằng
  // subquery trên entity Ancillary cùng persistence unit thay vì traversal `Ancillary_Type`.
  @Query("""
      select fca from FlightCabinAncillary fca
      where fca.flightId = :flightId
        and fca.cabinClassId = :cabinClassId
        and fca.ancillaryId in (select a.id from Ancillary a where a.type = :type)
      """)
  List<FlightCabinAncillary> findByFlightCabinAndAncillaryType(
      @Param("flightId") UUID flightId,
      @Param("cabinClassId") UUID cabinClassId,
      @Param("type") AncillaryType type);
}
