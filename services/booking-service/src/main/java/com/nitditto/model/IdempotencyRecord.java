package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một lần dùng {@code Idempotency-Key} của một user. Unique (user_id, idempotency_key) là khoá chống tạo trùng: request
 * thứ hai cùng key không insert được và đọc lại bản ghi này thay vì chạy lại nghiệp vụ.
 */
@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @UniqueConstraint(name = "uk_idempotency_user_key", columnNames = {"user_id", "idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord extends BaseEntity {

  public enum Status { IN_PROGRESS, COMPLETED }

  @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false)
  private UUID userId;

  @Column(name = "idempotency_key", length = 100, nullable = false)
  private String idempotencyKey;

  /** SHA-256 (hex) của body request: cùng key mà khác body là lỗi phía client (422). */
  @Column(name = "request_hash", length = 64, nullable = false)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  /** JSON của response đã trả lần đầu; chỉ có khi COMPLETED. */
  @Lob
  @Column(name = "response_body", columnDefinition = "TEXT")
  private String responseBody;

  /** Thời điểm nhận quyền xử lý: dùng tính hết hạn 24h và phát hiện request IN_PROGRESS bị bỏ dở. */
  @Column(name = "claimed_at", nullable = false)
  private Instant claimedAt;
}
