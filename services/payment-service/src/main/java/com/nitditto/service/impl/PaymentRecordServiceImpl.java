package com.nitditto.service.impl;

import com.nitditto.enums.PaymentStatus;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Payment;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.PaymentLinkResponse;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.service.PaymentRecordService;
import com.nitditto.service.gateway.ExchangeRateService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public Payment createPending(final PaymentInitiateRequest request, final ExchangeRateService.Quote quote) {
        final Payment.PaymentBuilder builder = Payment.builder()
                .userId(request.getUserId())
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .provider(request.getPaymentGateway())
                .status(PaymentStatus.PENDING);
        if (quote != null) {
            builder.chargeCurrency(quote.currency()).chargeAmountMinor(quote.amountMinor()).fxRate(quote.rate())
                    .fxMarginPercent(quote.marginPercent()).fxSource(quote.source());
        }
        return this.paymentRepository.save(builder.build());
    }

    @Override
    @Transactional
    public void attachLink(final UUID paymentId, final PaymentLinkResponse link) {
        final Payment payment = this.load(paymentId);
        payment.setProviderPaymentId(link.getPaymentLinkId());
        payment.setCheckoutUrl(link.getPaymentLinkUrl());
        payment.setLinkExpiresAt(link.getExpiresAt());
        if (link.getCreateDate() != null) {
            payment.setProviderCreateDate(link.getCreateDate());
        }
        this.paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public void markLinkCreationFailed(final UUID paymentId, final String reason) {
        final Payment payment = this.load(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        this.paymentRepository.save(payment);
        log.warn("Payment {} (bookingId={}) failed before a link could be created: {}", paymentId,
                payment.getBookingId(), reason);
    }

    private Payment load(final UUID paymentId) {
        return this.paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
    }
}
