package com.nitditto.repository;

import com.nitditto.enums.RefundStatus;
import com.nitditto.model.Refund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

  Optional<Refund> findByPaymentId(UUID paymentId);

  Page<Refund> findByStatus(RefundStatus status, Pageable pageable);
}
