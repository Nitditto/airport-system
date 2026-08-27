package com.nitditto.repository;

import com.nitditto.model.Fare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FareRepository extends JpaRepository<Fare, UUID> {

    boolean existsByFlightIdAndCabinClassIdAndName(UUID flightId, UUID cabinClassId, String name);
    List<Fare> findByFlightIdAndCabinClassId(UUID flightId, UUID cabinClassId);

    List<Fare> findByFlightIdInAndCabinClassId(List<UUID> flightIds, UUID cabinClassId);

    boolean existsByFlightIdAndCabinClassIdAndNameAndIdNot(UUID flightId, UUID cabinClassId, String name, UUID id);
}
