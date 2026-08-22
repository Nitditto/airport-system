package com.nitditto.repository;

import com.nitditto.model.Fare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FareRepository extends JpaRepository<Fare, Long> {

    boolean existsByFlightIdAndCabinClassIdAndName(Long flightId, Long cabinClassId, String name);
    List<Fare> findByFlightIdAndCabinClassId(Long flightId, Long cabinClassId);

    List<Fare> findByFlightIdInAndCabinClassId(List<Long> flightIds, Long cabinClassId);

    boolean existsByFlightIdAndCabinClassIdAndNameAndIdNot(Long flightId, Long cabinClassId, String name, Long id);
}
