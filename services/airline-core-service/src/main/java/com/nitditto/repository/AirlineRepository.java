package com.nitditto.repository;

import com.nitditto.enums.AirlineStatus;
import com.nitditto.model.Airline;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AirlineRepository extends JpaRepository<Airline, UUID> {

  Optional<Airline> findByOwnerId(UUID ownerId);

  List<Airline> findByStatus(AirlineStatus status);
}
