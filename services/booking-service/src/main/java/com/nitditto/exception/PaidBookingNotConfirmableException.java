package com.nitditto.exception;

import com.nitditto.enums.BookingStatus;
import java.util.UUID;

/**
 * Khách ĐÃ trả tiền (payment SUCCESS) nhưng booking không thể xác nhận: booking đã huỷ/hết hạn trước khi tiền về, hoặc ghế
 * đã bị người khác lấy. Là {@link BusinessException} nên code cũ bắt BusinessException vẫn đúng; nơi nào xử lý được thì bắt
 * riêng lớp này để hoàn tiền (xem {@code PaymentSettlementService}).
 */
public class PaidBookingNotConfirmableException extends BusinessException {

    private final UUID bookingId;
    private final UUID paymentId;
    private final BookingStatus bookingStatus;

    public PaidBookingNotConfirmableException(final UUID bookingId, final UUID paymentId,
            final BookingStatus bookingStatus, final String message) {
        super(message);
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.bookingStatus = bookingStatus;
    }

    public UUID getBookingId() {
        return this.bookingId;
    }

    public UUID getPaymentId() {
        return this.paymentId;
    }

    public BookingStatus getBookingStatus() {
        return this.bookingStatus;
    }
}
