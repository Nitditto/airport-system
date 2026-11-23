package com.nitditto.seat_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.model.FlightInstanceCabin;
import com.nitditto.repository.FlightInstanceCabinRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MySQL thật (schema {@code airline_seat_test}): nhiều giao dịch cùng cộng vào một cabin không được mất lượt nào,
 * và bộ đếm không xuống dưới 0.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_seat_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.kafka.listener.auto-startup=false"
})
class FlightInstanceCabinCounterE2ETest {

  private static final int THREADS = 20;

  @Autowired
  private FlightInstanceCabinRepository repository;
  @Autowired
  private TransactionTemplate transactionTemplate;

  private final List<UUID> created = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    this.repository.deleteAllById(this.created);
  }

  @Test
  void concurrentIncrements_areAllCounted() throws Exception {
    final UUID cabinId = this.cabinWithBooked(0);
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final CountDownLatch start = new CountDownLatch(1);
    final List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      results.add(pool.submit(() -> {
        start.await();
        return this.transactionTemplate.execute(status -> this.repository.adjustBookedSeats(cabinId, 1, Instant.now()));
      }));
    }
    start.countDown();
    for (final Future<?> result : results) {
      result.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    assertThat(this.repository.findById(cabinId).orElseThrow().getBookedSeats()).isEqualTo(THREADS);
  }

  @Test
  void decrementBelowZero_isClampedToZero() {
    final UUID cabinId = this.cabinWithBooked(2);

    this.transactionTemplate.executeWithoutResult(status -> this.repository.adjustBookedSeats(cabinId, -5, Instant.now()));

    assertThat(this.repository.findById(cabinId).orElseThrow().getBookedSeats()).isZero();
  }

  private UUID cabinWithBooked(final int booked) {
    final FlightInstanceCabin cabin = this.repository.save(FlightInstanceCabin.builder()
        .flightInstanceId(UUID.randomUUID()).cabinClassId(UUID.randomUUID())
        .totalSeats(100).bookedSeats(booked).baseFare(0.0).build());
    this.created.add(cabin.getId());
    return cabin.getId();
  }
}
