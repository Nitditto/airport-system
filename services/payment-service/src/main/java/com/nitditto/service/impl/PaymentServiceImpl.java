package com.nitditto.service.impl;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.event.PaymentSucceededEvent;
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
import com.nitditto.service.gateway.VNPayService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
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
    private final VNPayService vnPayService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public PaymentInitiateResponse initiatePayment(final PaymentInitiateRequest request) {
        log.info("Initiating payment for bookingId={}, userId={}, gateway={}", request.getBookingId(), request.getUserId(), request.getPaymentGateway());
        if (this.paymentRepository.existsByBookingIdAndStatus(request.getBookingId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException("Booking already has a successful payment: " + request.getBookingId());
        }

        Payment payment = Payment.builder()
                .userId(request.getUserId())
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .provider(request.getPaymentGateway())
                .status(PaymentStatus.PENDING)
                .build();
        payment = this.paymentRepository.save(payment);

        final PaymentLinkResponse paymentLink;
        if (request.getPaymentGateway() == PaymentGateway.VNPAY) {
            paymentLink = this.vnPayService.createPaymentLink(payment, "127.0.0.1");
        } else if (request.getPaymentGateway() == PaymentGateway.RAZORPAY) {
            // todo: thay bằng gọi user-service thật (RestClient/WebClient) khi có; hiện đang stub
            final UserDTO user = new UserDTO();
            user.setId(request.getUserId());
            user.setFullName("Nitditto");
            user.setEmail("nitditto@gmail.com");
            user.setPhone("0987654321");

            try {
                paymentLink = this.razorpayService.createPaymentLink(user, payment);
            } catch (RazorpayException ex) {
                log.error("Razorpay createPaymentLink failed for paymentId={}", payment.getId(), ex);
                throw new BusinessException("Failed to create payment link: " + ex.getMessage());
            }
        } else {
            throw new BusinessException("Payment gateway not supported yet: " + request.getPaymentGateway());
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
        if (request.getGateway() == PaymentGateway.VNPAY || (request.getVnpTxnRef() != null && !request.getVnpTxnRef().isBlank())) {
            final Payment payment = this.findByVnpTxnRef(request.getVnpTxnRef());
            // Không có tham số + chữ ký gốc từ VNPay thì không thể chứng minh khách đã trả tiền.
            if (request.getVnpParams() == null || request.getVnpParams().isEmpty()) {
                throw new BusinessException("Missing VNPay parameters");
            }
            return this.processVnpayResult(payment, request.getVnpParams());
        }

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
            this.markSuccess(payment, request.getRazorpayPaymentId());
        } else if ("failed".equalsIgnoreCase(status) && payment.getStatus() != PaymentStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(paymentDetails.optString("error_description", "Payment verification failed"));
            log.warn("Payment {} failed for bookingId={}: {}", payment.getId(), payment.getBookingId(), payment.getFailureReason());
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
        // Một booking có thể có nhiều lần thanh toán (thử lại): payment SUCCESS thắng, còn lại lấy bản ghi mới nhất.
        return this.paymentRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(
                        Payment::getBookingId,
                        PaymentMapper::toDTO,
                        PaymentServiceImpl::preferSuccessful));
    }

    private static PaymentDTO preferSuccessful(final PaymentDTO existing, final PaymentDTO replacement) {
        if (existing.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return existing;
        }
        return replacement;
    }

    @Override
    @Transactional
    public PaymentDTO checkPaymentVNPay(final Map<String, String> params) {
        log.info("Checking VNPay payment status for params keys: {}", params == null ? null : params.keySet());
        if (params == null || params.isEmpty()) {
            throw new BusinessException("Missing query parameters for VNPay check");
        }

        final Payment payment = this.findPaymentForVnpayCheck(params);

        final String responseCode = params.get("vnp_ResponseCode");
        if (responseCode == null || responseCode.isBlank()) {
            // Chỉ hỏi trạng thái hiện tại (không kèm kết quả từ cổng): trả về, không đổi gì.
            return PaymentMapper.toDTO(payment);
        }
        return this.processVnpayResult(payment, params);
    }

    /**
     * Xử lý kết quả VNPay trả về. BẮT BUỘC chữ ký hợp lệ: trước đây thiếu vnp_SecureHash thì bỏ qua bước kiểm tra, nghĩa
     * là ai cũng có thể tự gọi endpoint với vnp_ResponseCode=00 để đánh dấu payment (và nay là cả booking) đã thanh toán.
     */
    private PaymentDTO processVnpayResult(final Payment payment, final Map<String, String> params) {
        final Map<String, String> vnpFields = onlyVnpFields(params);
        if (!this.vnPayService.verifySignature(vnpFields, vnpFields.get("vnp_SecureHash"))) {
            throw new BusinessException("Invalid VNPay signature");
        }
        if (!payment.getId().toString().equals(vnpFields.get("vnp_TxnRef"))) {
            throw new BusinessException("VNPay reference does not match the payment");
        }
        this.requireMatchingAmount(payment, vnpFields.get("vnp_Amount"));

        // Một payment đã thanh toán thành công không bao giờ bị hạ xuống FAILED bởi lần gọi lặp/giả sau đó.
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return PaymentMapper.toDTO(payment);
        }

        final String responseCode = vnpFields.get("vnp_ResponseCode");
        final String transactionNo = vnpFields.get("vnp_TransactionNo");
        if (transactionNo != null && !transactionNo.isBlank()) {
            payment.setProviderPaymentId(transactionNo);
        }
        if ("00".equals(responseCode)) {
            this.markSuccess(payment, transactionNo);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(getVNPayErrorMessage(responseCode));
            log.warn("VNPay: Payment {} is FAILED with code: {}", payment.getId(), responseCode);
        }
        this.paymentRepository.save(payment);
        return PaymentMapper.toDTO(payment);
    }

    private void requireMatchingAmount(final Payment payment, final String vnpAmount) {
        if (vnpAmount == null || payment.getAmount() == null) {
            return;
        }
        final BigDecimal expected = BigDecimal.valueOf(payment.getAmount()).multiply(BigDecimal.valueOf(100));
        if (expected.compareTo(new BigDecimal(vnpAmount)) != 0) {
            throw new BusinessException("VNPay amount does not match the payment amount");
        }
    }

    // Chuyển sang SUCCESS và báo booking-service SAU KHI commit; gọi lặp lại (đã SUCCESS) thì không báo thêm.
    private void markSuccess(final Payment payment, final String transactionId) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        if (transactionId != null && !transactionId.isBlank()) {
            payment.setTransactionId(transactionId);
        }
        payment.setPaidAt(LocalDateTime.now());
        log.info("Payment {} captured successfully for bookingId={}", payment.getId(), payment.getBookingId());
        this.eventPublisher.publishEvent(new PaymentSucceededEvent(payment.getId(), payment.getBookingId()));
    }

    private Payment findByVnpTxnRef(final String txnRef) {
        try {
            return this.paymentRepository.findById(UUID.fromString(txnRef))
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", txnRef));
        } catch (final IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("Invalid VNPay transaction reference");
        }
    }

    private Payment findPaymentForVnpayCheck(final Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            txnRef = params.get("paymentId");
        }

        Payment payment = null;
        if (txnRef != null && !txnRef.isBlank()) {
            try {
                payment = this.paymentRepository.findById(UUID.fromString(txnRef)).orElse(null);
            } catch (final IllegalArgumentException ignored) {
            }
        }

        if (payment == null) {
            final String bookingIdStr = params.get("bookingId");
            if (bookingIdStr != null && !bookingIdStr.isBlank()) {
                try {
                    final List<Payment> payments = this.paymentRepository
                            .findByBookingIdIn(List.of(UUID.fromString(bookingIdStr)));
                    if (!payments.isEmpty()) {
                        payment = payments.get(payments.size() - 1);
                    }
                } catch (final IllegalArgumentException ignored) {
                }
            }
        }

        if (payment == null) {
            throw new ResourceNotFoundException("Payment", "reference", txnRef != null ? txnRef : params.toString());
        }
        return payment;
    }

    // Chữ ký VNPay chỉ tính trên các field vnp_*; field khác (paymentId, bookingId...) không được đưa vào.
    private static Map<String, String> onlyVnpFields(final Map<String, String> params) {
        final Map<String, String> fields = new HashMap<>();
        params.forEach((key, value) -> {
            if (key != null && key.startsWith("vnp_")) {
                fields.put(key, value);
            }
        });
        return fields;
    }

    private String getVNPayErrorMessage(final String code) {
        return switch (code) {
            case "00" -> "Transaction successful";
            case "07" -> "Suspected fraudulent transaction";
            case "09" -> "Customer card/account not registered for Internet Banking";
            case "10" -> "Customer authentication failed more than 3 times";
            case "11" -> "Payment session timed out";
            case "12" -> "Customer card/account is locked";
            case "24" -> "Transaction cancelled by customer";
            case "51" -> "Insufficient account balance";
            case "65" -> "Daily transaction limit exceeded";
            case "75" -> "Bank system under maintenance";
            default -> "VNPay transaction failed with response code: " + code;
        };
    }
}
