package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.IdempotencyConflictException;
import com.nitditto.model.IdempotencyRecord;
import com.nitditto.model.IdempotencyRecord.Status;
import com.nitditto.repository.IdempotencyRecordRepository;
import com.nitditto.service.IdempotencyService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Idempotency dựa trên bảng {@code idempotency_records} (unique user + key).
 *
 * <p>Cố ý KHÔNG {@code @Transactional}: mỗi lời gọi repository tự commit (claim → nghiệp vụ → lưu kết quả), nhờ vậy
 * request song song cùng key thấy ngay bản ghi IN_PROGRESS và nhận 409 thay vì chạy nghiệp vụ lần hai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

  static final int MAX_KEY_LENGTH = 100;
  /** Sau khoảng này key được coi là hết hạn và dùng lại được cho request mới. */
  static final Duration RETENTION = Duration.ofHours(24);
  /** IN_PROGRESS lâu hơn khoảng này coi như request đã chết giữa chừng (crash) và cho request sau giành lại. */
  static final Duration STALE_IN_PROGRESS = Duration.ofMinutes(5);
  private static final int MAX_CLAIM_ATTEMPTS = 3;

  private final IdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Override
  public <T> T execute(final UUID userId, final String idempotencyKey, final Object request,
      final Class<T> responseType, final Supplier<T> action) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return action.get();
    }
    final String key = idempotencyKey.trim();
    if (key.length() > MAX_KEY_LENGTH) {
      throw new BusinessException("Idempotency-Key must not exceed " + MAX_KEY_LENGTH + " characters");
    }
    final String requestHash = this.hash(request);

    final Claim<T> claim = this.claim(userId, key, requestHash, responseType);
    if (claim.replay() != null) {
      log.info("Idempotency-Key replay for user {}: returning stored response", userId);
      return claim.replay();
    }

    final IdempotencyRecord record = claim.record();
    final T result;
    try {
      result = action.get();
    } catch (final RuntimeException ex) {
      // Nghiệp vụ lỗi (đã tự rollback: nhả ghế, bỏ booking) -> giải phóng key để client retry được cùng key.
      this.repository.deleteById(record.getId());
      throw ex;
    }
    record.setStatus(Status.COMPLETED);
    record.setResponseBody(this.objectMapper.writeValueAsString(result));
    this.repository.save(record);
    return result;
  }

  private <T> Claim<T> claim(final UUID userId, final String key, final String requestHash,
      final Class<T> responseType) {
    for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
      final Instant now = this.clock.instant();
      try {
        final IdempotencyRecord created = this.repository.saveAndFlush(IdempotencyRecord.builder()
            .userId(userId).idempotencyKey(key).requestHash(requestHash)
            .status(Status.IN_PROGRESS).claimedAt(now).build());
        return Claim.claimed(created);
      } catch (final DataIntegrityViolationException duplicate) {
        final IdempotencyRecord existing = this.repository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing == null) {
          continue; // vừa bị xoá (request trước lỗi) -> thử insert lại
        }
        final boolean expired = existing.getClaimedAt().plus(RETENTION).isBefore(now);
        if (!expired) {
          if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Idempotency-Key was already used with a different request");
          }
          if (existing.getStatus() == Status.COMPLETED) {
            return Claim.replay(this.objectMapper.readValue(existing.getResponseBody(), responseType));
          }
          if (existing.getClaimedAt().plus(STALE_IN_PROGRESS).isAfter(now)) {
            throw new IdempotencyConflictException(HttpStatus.CONFLICT,
                "A request with this Idempotency-Key is still being processed");
          }
        }
        // Hết hạn 24h, hoặc IN_PROGRESS bị bỏ dở: giành lại bằng compare-and-set, chỉ một request thắng.
        if (this.repository.reclaim(existing.getId(), existing.getClaimedAt(), now, requestHash) == 1) {
          log.warn("Reclaimed {} Idempotency-Key for user {}", expired ? "expired" : "abandoned", userId);
          return Claim.claimed(this.repository.findById(existing.getId()).orElseThrow());
        }
      }
    }
    throw new IdempotencyConflictException(HttpStatus.CONFLICT,
        "A request with this Idempotency-Key is still being processed");
  }

  private String hash(final Object request) {
    try {
      final byte[] json = this.objectMapper.writeValueAsBytes(request);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (final NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private record Claim<T>(IdempotencyRecord record, T replay) {

    static <T> Claim<T> claimed(final IdempotencyRecord record) {
      return new Claim<>(record, null);
    }

    static <T> Claim<T> replay(final T response) {
      return new Claim<>(null, response);
    }
  }
}
