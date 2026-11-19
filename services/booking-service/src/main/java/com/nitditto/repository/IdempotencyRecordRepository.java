package com.nitditto.repository;

import com.nitditto.model.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

  Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

  /**
   * Giành lại một bản ghi bị bỏ dở / đã hết hạn theo kiểu compare-and-set: chỉ request nào còn thấy đúng
   * {@code expectedClaimedAt} mới cập nhật được (trả 1), nên hai request cùng giành thì chỉ một bên thắng.
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update IdempotencyRecord r
         set r.claimedAt = :now, r.status = com.nitditto.model.IdempotencyRecord.Status.IN_PROGRESS,
             r.requestHash = :requestHash, r.responseBody = null
       where r.id = :id and r.claimedAt = :expectedClaimedAt
      """)
  int reclaim(@Param("id") UUID id, @Param("expectedClaimedAt") Instant expectedClaimedAt,
      @Param("now") Instant now, @Param("requestHash") String requestHash);
}
