package com.nitditto.repository;

import com.nitditto.model.SeatInstance;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatInstanceRepository extends JpaRepository<SeatInstance, UUID> {

    List<SeatInstance> findByFlightInstanceCabinId(UUID flightInstanceCabinId);

    boolean existsByFlightInstanceCabinId(UUID flightInstanceCabinId);

    void deleteByFlightInstanceCabinId(UUID flightInstanceCabinId);

    // Khoá hàng (SELECT ... FOR UPDATE) để hai người giữ/đặt cùng một ghế phải xếp hàng, người đến sau thấy ghế đã bị giữ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatInstance s where s.id in :ids order by s.id")
    List<SeatInstance> lockAllByIdIn(@Param("ids") Collection<UUID> ids);

    List<SeatInstance> findByFlightInstanceIdInAndHeldBy(Collection<UUID> flightInstanceIds, UUID heldBy);
}
