package com.nitditto.event;

/**
 * Tên topic Kafka dùng chung cho producer và consumer. Luôn tham chiếu hằng số ở đây, không gõ chuỗi tay:
 * broker bật auto-create topic nên gõ sai tên sẽ tạo topic mới một cách im lặng và event không bao giờ tới nơi.
 */
public final class KafkaTopics {

  public static final String PAYMENT_COMPLETED = "payment-completed";
  public static final String PAYMENT_FAILED = "payment-failed";
  public static final String FLIGHT_INSTANCE_CREATED = "flight-instance-created";

  private KafkaTopics() {
  }
}
