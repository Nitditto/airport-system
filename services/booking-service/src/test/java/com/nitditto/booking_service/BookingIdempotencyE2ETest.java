package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.repository.IdempotencyRecordRepository;
import com.nitditto.service.BookingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * E2E HTTP → BookingController → IdempotencyService → MySQL {@code airline_booking_test} (bảng idempotency_records
 * thật, unique constraint thật). BookingService được mock để đếm số lần nghiệp vụ thực sự chạy.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_booking_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    // Không join consumer group thật nếu đang có Kafka ở localhost:9092 (vd. stack docker-compose đang chạy).
    "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class BookingIdempotencyE2ETest {

  private static final String BODY = """
      {"flightId":"%s","flightInstanceId":"%s","cabinClassType":"ECONOMY","fareId":"%s",
       "passengers":[{"firstName":"An","lastName":"Nguyen","email":"an@test.vn","phone":"0900000001",
                      "dateOfBirth":"1995-01-01","gender":"MALE","seatInstanceId":"%s"}]}
      """;

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private IdempotencyRecordRepository repository;
  @MockitoBean
  private BookingService bookingService;

  private final UUID userId = UUID.randomUUID();
  private String body;

  @BeforeEach
  void setUp() {
    this.repository.deleteAll();
    this.body = BODY.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    when(this.bookingService.createBooking(any(BookingRequest.class), eq(this.userId)))
        .thenAnswer(inv -> PaymentInitiateResponse.builder().paymentId(UUID.randomUUID())
            .checkoutUrl("https://sandbox.vnpayment.vn/pay/" + UUID.randomUUID()).success(true).build());
  }

  @AfterEach
  void tearDown() {
    this.repository.deleteAll();
  }

  @Test
  void sameKeyTwice_runsBookingOnce_andReturnsIdenticalResponse() throws Exception {
    final String key = UUID.randomUUID().toString();

    final String first = this.send(key, this.body).andExpect(status().isCreated()).andReturn()
        .getResponse().getContentAsString();
    final String second = this.send(key, this.body).andExpect(status().isCreated()).andReturn()
        .getResponse().getContentAsString();

    assertThat(second).isEqualTo(first);
    verify(this.bookingService, times(1)).createBooking(any(), eq(this.userId));
    assertThat(this.repository.count()).isEqualTo(1);
  }

  @Test
  void sameKey_differentBody_is422_andDoesNotRunBooking() throws Exception {
    final String key = UUID.randomUUID().toString();
    this.send(key, this.body).andExpect(status().isCreated());
    final String other = BODY.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    this.send(key, other).andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.success").value(false));

    verify(this.bookingService, times(1)).createBooking(any(), eq(this.userId));
  }

  @Test
  void withoutHeader_everyRequestCreatesABooking() throws Exception {
    this.send(null, this.body).andExpect(status().isCreated());
    this.send(null, this.body).andExpect(status().isCreated());

    verify(this.bookingService, times(2)).createBooking(any(), eq(this.userId));
    assertThat(this.repository.count()).isZero();
  }

  @Test
  void failedBooking_releasesKey_soTheSameKeyCanBeRetried() throws Exception {
    final String key = UUID.randomUUID().toString();
    when(this.bookingService.createBooking(any(BookingRequest.class), eq(this.userId)))
        .thenThrow(new com.nitditto.exception.BusinessException("Seat(s) already held by another user"))
        .thenAnswer(inv -> PaymentInitiateResponse.builder().paymentId(UUID.randomUUID()).success(true).build());

    this.send(key, this.body).andExpect(status().isBadRequest());
    assertThat(this.repository.count()).isZero();
    this.send(key, this.body).andExpect(status().isCreated());

    verify(this.bookingService, times(2)).createBooking(any(), eq(this.userId));
  }

  @Test
  void concurrentRequestsWithSameKey_onlyOneRunsBooking_theOtherGets409() throws Exception {
    final String key = UUID.randomUUID().toString();
    final CountDownLatch inBooking = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    when(this.bookingService.createBooking(any(BookingRequest.class), eq(this.userId))).thenAnswer(inv -> {
      inBooking.countDown();
      release.await(10, TimeUnit.SECONDS); // giữ request thứ nhất "đang xử lý"
      return PaymentInitiateResponse.builder().paymentId(UUID.randomUUID()).success(true).build();
    });

    final ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      final Future<MvcResult> first = pool.submit(() -> this.send(key, this.body).andReturn());
      assertThat(inBooking.await(10, TimeUnit.SECONDS)).isTrue();
      final Future<MvcResult> second = pool.submit(() -> this.send(key, this.body).andReturn());
      final int secondStatus = second.get(10, TimeUnit.SECONDS).getResponse().getStatus();
      release.countDown();
      final int firstStatus = first.get(10, TimeUnit.SECONDS).getResponse().getStatus();

      final List<Integer> statuses = new ArrayList<>(List.of(firstStatus, secondStatus));
      Collections.sort(statuses);
      assertThat(statuses).containsExactly(201, 409);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    verify(this.bookingService, times(1)).createBooking(any(), eq(this.userId));
  }

  private org.springframework.test.web.servlet.ResultActions send(final String key, final String json)
      throws Exception {
    final var request = post("/api/bookings").header("X-User-Id", this.userId.toString())
        .contentType(MediaType.APPLICATION_JSON).content(json);
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return this.mockMvc.perform(request);
  }
}
