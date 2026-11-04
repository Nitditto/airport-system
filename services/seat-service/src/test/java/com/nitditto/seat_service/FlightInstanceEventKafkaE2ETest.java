package com.nitditto.seat_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.nitditto.enums.CabinClassType;
import com.nitditto.enums.SeatType;
import com.nitditto.event.FlightInstanceCreatedEvent;
import com.nitditto.event.KafkaTopics;
import com.nitditto.model.CabinClass;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatInstance;
import com.nitditto.model.SeatMap;
import com.nitditto.repository.CabinClassRepository;
import com.nitditto.repository.FlightInstanceCabinRepository;
import com.nitditto.repository.SeatInstanceRepository;
import com.nitditto.repository.SeatMapRepository;
import com.nitditto.repository.SeatRepository;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Tích hợp thật: broker Kafka nhúng → @KafkaListener (deserializer, error handler của Boot) → MySQL schema
 * {@code airline_seat_test} (create-drop). Kiểm tra sinh ghế từ FlightInstanceCreatedEvent và tính idempotent khi
 * message tới lại.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_seat_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
    "seat.pricing.default-window-surcharge=1000.0",
    "seat.pricing.default-aisle-surcharge=500.0"
})
@EmbeddedKafka(partitions = 1, topics = KafkaTopics.FLIGHT_INSTANCE_CREATED,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class FlightInstanceEventKafkaE2ETest {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  @Autowired
  private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired
  private CabinClassRepository cabinClassRepository;
  @Autowired
  private SeatMapRepository seatMapRepository;
  @Autowired
  private SeatRepository seatRepository;
  @Autowired
  private FlightInstanceCabinRepository flightInstanceCabinRepository;
  @Autowired
  private SeatInstanceRepository seatInstanceRepository;

  private final UUID aircraftId = UUID.randomUUID();
  private UUID seatMapId;

  @BeforeEach
  void seedAircraftLayout() {
    this.cleanAll();
    final CabinClass economy = this.cabinClassRepository.save(CabinClass.builder()
        .name(CabinClassType.ECONOMY).code("Y").aircraftId(this.aircraftId).build());
    final SeatMap seatMap = this.seatMapRepository.save(SeatMap.builder()
        .name("A320 economy").totalRows(1).leftSeatsPerRow(2).rightSeatsPerRow(2)
        .airlineId(UUID.randomUUID()).cabinClassId(economy.getId()).build());
    this.seatMapId = seatMap.getId();
    this.seatRepository.saveAll(List.of(
        seat("1A", 'A', SeatType.WINDOW, 2000.0, economy.getId(), seatMap.getId()),
        seat("1B", 'B', SeatType.AISLE, null, economy.getId(), seatMap.getId()),
        seat("1C", 'C', SeatType.MIDDLE, null, economy.getId(), seatMap.getId()),
        seat("1D", 'D', SeatType.WINDOW, null, economy.getId(), seatMap.getId())));
  }

  @AfterEach
  void cleanAll() {
    this.seatInstanceRepository.deleteAll();
    this.flightInstanceCabinRepository.deleteAll();
    this.seatRepository.deleteAll();
    this.seatMapRepository.deleteAll();
    this.cabinClassRepository.deleteAll();
  }

  @Test
  void flightInstanceCreated_generatesSeatsWithSurchargePriority_andRedeliveryCreatesNoDuplicates() throws Exception {
    final UUID flightInstanceId = UUID.randomUUID();
    final FlightInstanceCreatedEvent event = new FlightInstanceCreatedEvent(flightInstanceId, this.aircraftId,
        UUID.randomUUID());

    this.kafkaTemplate.send(KafkaTopics.FLIGHT_INSTANCE_CREATED, flightInstanceId.toString(), event).get();

    await().atMost(TIMEOUT).untilAsserted(() ->
        assertThat(this.seatsOf(flightInstanceId)).hasSize(4));
    final List<SeatInstance> seats = this.seatsOf(flightInstanceId);
    assertThat(seats).allSatisfy(si -> assertThat(si.getSeatMapId()).isEqualTo(this.seatMapId));
    // 1A: giá riêng ghế mẫu 2000 · 1B: AISLE mặc định 500 · 1C: MIDDLE 0 · 1D: WINDOW mặc định 1000
    assertThat(this.surchargesBySeatNumber(seats)).containsExactly(2000.0, 500.0, 0.0, 1000.0);
    assertThat(this.flightInstanceCabinRepository.count()).isEqualTo(1);

    // Message tới lại (retry/rebalance): không được sinh thêm dòng nào.
    this.kafkaTemplate.send(KafkaTopics.FLIGHT_INSTANCE_CREATED, flightInstanceId.toString(), event).get();
    final UUID marker = UUID.randomUUID();
    final FlightInstanceCreatedEvent markerEvent = new FlightInstanceCreatedEvent(marker, this.aircraftId,
        UUID.randomUUID());
    // Cùng partition nên khi marker đã được xử lý thì message lặp ở trước nó chắc chắn đã xử lý xong.
    this.kafkaTemplate.send(KafkaTopics.FLIGHT_INSTANCE_CREATED, marker.toString(), markerEvent).get();
    await().atMost(TIMEOUT).untilAsserted(() -> assertThat(this.seatsOf(marker)).hasSize(4));

    assertThat(this.seatsOf(flightInstanceId)).hasSize(4);
    assertThat(this.flightInstanceCabinRepository.count()).isEqualTo(2);
  }

  private List<SeatInstance> seatsOf(final UUID flightInstanceId) {
    return this.seatInstanceRepository.findAll().stream()
        .filter(si -> flightInstanceId.equals(si.getFlightInstanceId())).toList();
  }

  private List<Double> surchargesBySeatNumber(final List<SeatInstance> seats) {
    final var seatNumberById = this.seatRepository.findAll().stream()
        .collect(java.util.stream.Collectors.toMap(Seat::getId, Seat::getSeatNumber));
    return seats.stream()
        .sorted(Comparator.comparing(si -> seatNumberById.get(si.getSeatId())))
        .map(SeatInstance::getPremiumSuperCharge)
        .toList();
  }

  private static Seat seat(final String number, final char column, final SeatType type, final Double surcharge,
      final UUID cabinClassId, final UUID seatMapId) {
    return Seat.builder().seatNumber(number).seatRow(1).columnLetter(column).seatType(type)
        .premiumSuperCharge(surcharge).seatMapId(seatMapId).cabinClassId(cabinClassId).build();
  }
}
