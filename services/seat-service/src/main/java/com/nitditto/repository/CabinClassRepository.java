package com.nitditto.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nitditto.model.CabinClass;
import com.nitditto.enums.CabinClassType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CabinClassRepository extends JpaRepository<CabinClass, UUID> {

    List<CabinClass> findByAircraftId(UUID id);

    Optional<CabinClass> findByAircraftIdAndName(UUID id, CabinClassType name);

    boolean existsByCodeAndAircraftId(String code, UUID aircraftId);

    boolean existsByCodeAndAircraftIdAndIdNot(String code, UUID aircraftId, UUID id);

    boolean existsByAircraftIdAndName(UUID aircraftId, CabinClassType name);

    boolean existsByAircraftIdAndNameAndIdNot(UUID aircraftId, CabinClassType name, UUID id);

}
