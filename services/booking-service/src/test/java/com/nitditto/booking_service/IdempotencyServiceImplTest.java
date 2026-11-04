package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.IdempotencyConflictException;
import com.nitditto.model.IdempotencyRecord;
import com.nitditto.model.IdempotencyRecord.Status;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.repository.IdempotencyRecordRepository;
import com.nitditto.service.impl.IdempotencyServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

  @Mock
  private IdempotencyRecordRepository repository;

  private final ObjectMapper mapper = JsonMapper.builder().build();
  private IdempotencyServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private final Map<String, String> request = Map.of("fareId", "F1", "seat", "1A");
  private final AtomicInteger actionCalls = new AtomicInteger();

  @BeforeEach
  void setUp() {
    this.service = new IdempotencyServiceImpl(this.repository, this.mapper, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void withoutKey_runsActionEveryTime_andStoresNothing() {
    assertThat(this.run(null)).isNotNull();
    assertThat(this.run("  ")).isNotNull();

    assertThat(this.actionCalls).hasValue(2);
    verify(this.repository, never()).saveAndFlush(any());
  }

  @Test
  void firstUse_runsAction_andStoresCompletedResponse() {
    when(this.repository.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    final PaymentInitiateResponse result = this.run("key-1");

    assertThat(this.actionCalls).hasValue(1);
    final ArgumentCaptor<IdempotencyRecord> saved = ArgumentCaptor.forClass(IdempotencyRecord.class);
    verify(this.repository).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(Status.COMPLETED);
    assertThat(saved.getValue().getResponseBody()).contains(result.getPaymentId().toString());
    assertThat(saved.getValue().getRequestHash()).hasSize(64);
  }

  @Test
  void completedKey_withSameRequest_returnsStoredResponse_withoutRunningAction() {
    final PaymentInitiateResponse stored = PaymentInitiateResponse.builder()
        .paymentId(UUID.randomUUID()).checkoutUrl("https://pay/1").success(true).build();
    this.givenExisting(Status.COMPLETED, this.hashOfRequest(), this.mapper.writeValueAsString(stored), NOW);

    final PaymentInitiateResponse replay = this.run("key-1");

    assertThat(this.actionCalls).hasValue(0);
    assertThat(replay.getPaymentId()).isEqualTo(stored.getPaymentId());
    assertThat(replay.getCheckoutUrl()).isEqualTo("https://pay/1");
  }

  @Test
  void sameKey_withDifferentRequest_is422() {
    this.givenExisting(Status.COMPLETED, "0".repeat(64), "{}", NOW);

    assertThatThrownBy(() -> this.run("key-1"))
        .isInstanceOfSatisfying(IdempotencyConflictException.class,
            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    assertThat(this.actionCalls).hasValue(0);
  }

  @Test
  void inProgressKey_isRecent_is409() {
    this.givenExisting(Status.IN_PROGRESS, this.hashOfRequest(), null, NOW.minusSeconds(10));

    assertThatThrownBy(() -> this.run("key-1"))
        .isInstanceOfSatisfying(IdempotencyConflictException.class,
            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(this.actionCalls).hasValue(0);
  }

  @Test
  void abandonedInProgressKey_isReclaimed_andActionRuns() {
    final IdempotencyRecord stale = this.givenExisting(Status.IN_PROGRESS, this.hashOfRequest(), null,
        NOW.minus(Duration.ofMinutes(6)));
    when(this.repository.reclaim(eq(stale.getId()), eq(stale.getClaimedAt()), eq(NOW), anyString())).thenReturn(1);
    when(this.repository.findById(stale.getId())).thenReturn(Optional.of(stale));

    this.run("key-1");

    assertThat(this.actionCalls).hasValue(1);
  }

  @Test
  void reclaimLostToAnotherRequest_ends409_withoutRunningAction() {
    final IdempotencyRecord stale = this.givenExisting(Status.IN_PROGRESS, this.hashOfRequest(), null,
        NOW.minus(Duration.ofMinutes(6)));
    when(this.repository.reclaim(any(), any(), any(), anyString())).thenReturn(0);

    assertThatThrownBy(() -> this.run("key-1")).isInstanceOf(IdempotencyConflictException.class);
    assertThat(this.actionCalls).hasValue(0);
    assertThat(stale.getStatus()).isEqualTo(Status.IN_PROGRESS);
  }

  @Test
  void keyOlderThan24h_isTreatedAsNew_evenWithDifferentRequest() {
    final IdempotencyRecord old = this.givenExisting(Status.COMPLETED, "0".repeat(64), "{}",
        NOW.minus(Duration.ofHours(25)));
    when(this.repository.reclaim(eq(old.getId()), any(), eq(NOW), anyString())).thenReturn(1);
    when(this.repository.findById(old.getId())).thenReturn(Optional.of(old));

    this.run("key-1");

    assertThat(this.actionCalls).hasValue(1);
  }

  @Test
  void failingAction_releasesKey_andRethrows() {
    when(this.repository.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    assertThatThrownBy(() -> this.service.execute(this.userId, "key-1", this.request,
        PaymentInitiateResponse.class, () -> {
          throw new BusinessException("Seat(s) already held");
        })).isInstanceOf(BusinessException.class).hasMessageContaining("already held");

    verify(this.repository).deleteById(any());
    verify(this.repository, never()).save(any());
  }

  @Test
  void keyLongerThan100_isRejected() {
    assertThatThrownBy(() -> this.run("k".repeat(101))).isInstanceOf(BusinessException.class);
    assertThat(this.actionCalls).hasValue(0);
  }

  private PaymentInitiateResponse run(final String key) {
    return this.service.execute(this.userId, key, this.request, PaymentInitiateResponse.class, () -> {
      this.actionCalls.incrementAndGet();
      return PaymentInitiateResponse.builder().paymentId(UUID.randomUUID()).success(true).build();
    });
  }

  private IdempotencyRecord givenExisting(final Status status, final String hash, final String body,
      final Instant claimedAt) {
    final IdempotencyRecord existing = withId(IdempotencyRecord.builder().userId(this.userId)
        .idempotencyKey("key-1").requestHash(hash).status(status).responseBody(body).claimedAt(claimedAt).build());
    when(this.repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
    when(this.repository.findByUserIdAndIdempotencyKey(this.userId, "key-1")).thenReturn(Optional.of(existing));
    return existing;
  }

  private String hashOfRequest() {
    try {
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(this.mapper.writeValueAsBytes(this.request)));
    } catch (final java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static IdempotencyRecord withId(final IdempotencyRecord record) {
    if (record.getId() == null) {
      record.setId(UUID.randomUUID());
    }
    return record;
  }
}
