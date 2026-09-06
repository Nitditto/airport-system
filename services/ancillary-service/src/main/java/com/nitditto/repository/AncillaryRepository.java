package com.nitditto.repository;

import com.nitditto.model.Ancillary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AncillaryRepository extends JpaRepository<Ancillary, UUID> {
  List<Ancillary> findByAirlineId(UUID airlineId);
}
