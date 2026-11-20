package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.model.Payment;
import com.nitditto.repository.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Ràng buộc "một payment thành công cho mỗi booking" trên MySQL thật ({@code airline_payment_test}): unique constraint là
 * lớp bảo vệ cuối cùng khi hai kết quả thanh toán của cùng một booking về song song và cùng vượt qua bước kiểm tra ở code.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_payment_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false",
    "razorpay.api.key=rzp_test_dummy",
    "razorpay.api.secret=dummy",
    "vnpay.tmn-code=TESTTMN",
    "vnpay.hash-secret=single-success-secret"
})
class SingleSuccessPerBookingE2ETest {

  @Autowired
  private PaymentRepository paymentRepository;

  @AfterEach
  void cleanUp() {
    this.paymentRepository.deleteAll();
  }

  private Payment payment(final UUID bookingId, final PaymentStatus status, final UUID paidBookingId) {
    return Payment.builder().userId(UUID.randomUUID()).bookingId(bookingId).amount(100.0)
        .provider(PaymentGateway.VNPAY).status(status).paidBookingId(paidBookingId).build();
  }

  @Test
  void theDatabaseRefusesASecondSuccessfulPaymentForTheSameBooking() {
    final UUID bookingId = UUID.randomUUID();
    this.paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.SUCCESS, bookingId));

    assertThatThrownBy(
        () -> this.paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.SUCCESS, bookingId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void manyNonSuccessfulPaymentsForOneBookingAreAllowed_soRetriesAndDuplicatesCanBeRecorded() {
    final UUID bookingId = UUID.randomUUID();
    this.paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.SUCCESS, bookingId));

    this.paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.PENDING, null));
    this.paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.FAILED, null));
    this.paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.DUPLICATE, null));

    assertThat(this.paymentRepository.findByBookingIdIn(java.util.List.of(bookingId))).hasSize(4);
  }
}
