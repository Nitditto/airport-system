package com.nitditto.service;

import com.nitditto.model.Payment;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.PaymentLinkResponse;
import com.nitditto.service.gateway.ExchangeRateService;
import java.util.UUID;

/**
 * Các bước GHI DB ngắn của luồng tạo thanh toán, mỗi bước một transaction riêng và commit ngay. Tách khỏi
 * {@code PaymentService.initiatePayment} để lời gọi mạng (user-service, cổng thanh toán) chạy NGOÀI mọi transaction DB
 * (CLAUDE.md §8.5, §8.6): bean riêng vì gọi {@code this.method()} trong cùng class bỏ qua proxy {@code @Transactional}.
 */
public interface PaymentRecordService {

    /**
     * Ghi payment {@code PENDING} (chưa có link) và commit. {@code quote} khác null khi cổng thu loại tiền khác VND: lưu cùng
     * payment số tiền khách thật sự phải trả và tỷ giá đã dùng.
     */
    Payment createPending(PaymentInitiateRequest request, ExchangeRateService.Quote quote);

    /** Gắn link đã tạo ở cổng vào payment (id cổng, URL, ngày tạo giao dịch VNPay) và commit. */
    void attachLink(UUID paymentId, PaymentLinkResponse link);

    /**
     * Không tạo được link: đánh dấu payment {@code FAILED} để không để lại payment {@code PENDING} mồ côi. Cố ý KHÔNG phát
     * event thất bại: booking-service tự bù trừ khi {@code initiate} ném lỗi (nhả ghế, bỏ booking).
     */
    void markLinkCreationFailed(UUID paymentId, String reason);
}
