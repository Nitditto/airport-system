package com.nitditto.service.impl;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.PaymentMapper;
import com.nitditto.model.Payment;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.request.PaymentVerifyRequest;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.payload.response.PaymentLinkResponse;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.service.PaymentService;
import com.nitditto.service.gateway.RazorpayService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayService razorpayService;

    @Override
    @Transactional
    public PaymentInitiateResponse initiatePayment(final PaymentInitiateRequest request) {
        log.info("Initiating payment for bookingId={}, userId={}", request.getBookingId(), request.getUserId());
        if (this.paymentRepository.existsByBookingIdAndStatus(request.getBookingId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException("Booking already has a successful payment: " + request.getBookingId());
        }

        if (request.getPaymentGateway() != PaymentGateway.RAZORPAY) {
            throw new BusinessException("Payment gateway not supported yet: " + request.getPaymentGateway());
        }

        Payment payment = Payment.builder()
                .userId(request.getUserId())
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .provider(request.getPaymentGateway())
                .status(PaymentStatus.PENDING)
                .build();
        payment = this.paymentRepository.save(payment);

        // todo: thay bằng gọi user-service thật (RestClient/WebClient) khi có; hiện đang stub
        final UserDTO user = new UserDTO();
        user.setId(request.getUserId());
        user.setFullName("Nitditto");
        user.setEmail("nitditto@gmail.com");
        user.setPhone("0987654321");

        final PaymentLinkResponse paymentLink;
        try {
            paymentLink = this.razorpayService.createPaymentLink(user, payment);
        } catch (RazorpayException ex) {
            log.error("Razorpay createPaymentLink failed for paymentId={}", payment.getId(), ex);
            throw new BusinessException("Failed to create payment link: " + ex.getMessage());
        }

        payment.setProviderPaymentId(paymentLink.getPaymentLinkId());
        this.paymentRepository.save(payment);

        return PaymentInitiateResponse.builder()
                .paymentId(payment.getId())
                .paymentGateway(request.getPaymentGateway())
                .amount(request.getAmount())
                .description(request.getDescription())
                .providerReferenceId(paymentLink.getPaymentLinkId())
                .checkoutUrl(paymentLink.getPaymentLinkUrl())
                .success(true)
                .message("Payment initiated successfully")
                .build();
    }

    @Override
    @Transactional
    public PaymentDTO verifyPayment(final PaymentVerifyRequest request) {
        final JSONObject paymentDetails;
        try {
            paymentDetails = this.razorpayService.fetchPaymentDetails(request.getRazorpayPaymentId());
        } catch (RazorpayException ex) {
            log.error("Razorpay fetchPaymentDetails failed for razorpayPaymentId={}", request.getRazorpayPaymentId(), ex);
            throw new BusinessException("Failed to fetch payment details: " + ex.getMessage());
        }

        final JSONObject notes = paymentDetails.optJSONObject("notes");
        final String paymentIdFromNotes = notes == null ? null : notes.optString("payment_id", null);
        if (paymentIdFromNotes == null) {
            throw new BusinessException("Razorpay payment has no linked payment_id in notes");
        }

        final Payment payment = this.paymentRepository.findById(UUID.fromString(paymentIdFromNotes))
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentIdFromNotes));

        final String status = paymentDetails.optString("status");
        payment.setProviderPaymentId(request.getRazorpayPaymentId());

        if ("captured".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(request.getRazorpayPaymentId());
            payment.setPaidAt(LocalDateTime.now());
            log.info("Payment {} captured successfully for bookingId={}", payment.getId(), payment.getBookingId());
            // todo: publish/notify booking-service qua PATCH /api/bookings/{id}/confirm-payment (xem docs/features/payment-service.md)
        } else if ("failed".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(paymentDetails.optString("error_description", "Payment verification failed"));
            log.warn("Payment {} failed for bookingId={}: {}", payment.getId(), payment.getBookingId(), payment.getFailureReason());
            // todo: publish/notify booking-service qua PATCH /api/bookings/{id}/cancel-payment
        } else {
            // created/authorized: Razorpay chưa xử lý xong, giữ nguyên PENDING để client retry verify
            log.info("Payment {} still in intermediate Razorpay status={}", payment.getId(), status);
        }

        this.paymentRepository.save(payment);
        return PaymentMapper.toDTO(payment);
    }

    @Override
    public Page<PaymentDTO> getAllPayments(final UUID userId, final Pageable pageable) {
        return this.paymentRepository.findByUserId(userId, pageable).map(PaymentMapper::toDTO);
    }

    @Override
    public Map<UUID, PaymentDTO> getPaymentsByBookingIds(final List<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.paymentRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(
                        Payment::getBookingId,
                        PaymentMapper::toDTO,
                        (existing, replacement) -> replacement));
    }
}
