package com.nitditto.service.impl;

import com.nitditto.client.UserClient;
import com.nitditto.enums.PaymentGateway;
import com.nitditto.exception.ForbiddenException;
import com.nitditto.payload.response.PaymentGatewayResponse;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.event.PaymentFailedEvent;
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
import com.nitditto.service.gateway.VnpayIpnResponse;
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
import java.time.Clock;
import java.time.Duration;
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
    private final UserClient userClient;
    private final Clock clock;

    // Link VNPay hết hạn sau 15 phút: chỉ trả lại link cũ khi khách còn dư thời gian để dùng nó.
    private static final Duration REUSE_WINDOW = Duration.ofMinutes(10);

    @Override
    public List<PaymentGatewayResponse> getAvailableGateways() {
        return List.of(
                PaymentGatewayResponse.builder().code(PaymentGateway.RAZORPAY).name("Razorpay")
                        .enabled(this.razorpayService.isConfigured()).build(),
                PaymentGatewayResponse.builder().code(PaymentGateway.VNPAY).name("VNPay")
                        .enabled(this.vnPayService.isConfigured()).build());
    }

    @Override
    @Transactional
    public PaymentInitiateResponse initiatePayment(final PaymentInitiateRequest request) {
        log.info("Initiating payment for bookingId={}, userId={}, gateway={}", request.getBookingId(), request.getUserId(), request.getPaymentGateway());
        if (!this.paymentRepository.findByBookingIdAndStatusIn(request.getBookingId(),
                List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED)).isEmpty()) {
            throw new BusinessException("Booking already has a successful payment: " + request.getBookingId());
        }
        final PaymentInitiateResponse reused = this.reusablePendingPayment(request);
        if (reused != null) {
            return reused;
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
            payment.setProviderCreateDate(paymentLink.getCreateDate());
        } else if (request.getPaymentGateway() == PaymentGateway.RAZORPAY) {
            // todo: thay bằng gọi user-service thật (RestClient/WebClient) khi có; hiện đang stub
            final UserDTO user = this.userClient.getUserById(request.getUserId()).getBody().getData();

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
        payment.setCheckoutUrl(paymentLink.getPaymentLinkUrl());
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

    /**
     * `initiate` gọi lặp (bấm đúp, retry, nhiều tab) cho cùng booking: trả lại link còn hạn thay vì tạo thêm payment, để
     * khách không có hai link cùng trả được. Chỉ tái dùng khi cùng người, cùng cổng, cùng số tiền.
     */
    private PaymentInitiateResponse reusablePendingPayment(final PaymentInitiateRequest request) {
        final Payment pending = this.paymentRepository.findFirstByBookingIdAndProviderAndStatusOrderByCreatedAtDesc(
                request.getBookingId(), request.getPaymentGateway(), PaymentStatus.PENDING).orElse(null);
        if (pending == null || pending.getCheckoutUrl() == null || pending.getCheckoutUrl().isBlank()
                || !request.getUserId().equals(pending.getUserId())
                || !java.util.Objects.equals(request.getAmount(), pending.getAmount())
                || pending.getCreatedAt() == null
                || !pending.getCreatedAt().plus(REUSE_WINDOW).isAfter(this.clock.instant())) {
            return null;
        }
        log.info("Reusing pending paymentId={} for bookingId={}", pending.getId(), pending.getBookingId());
        return PaymentInitiateResponse.builder()
                .paymentId(pending.getId())
                .paymentGateway(pending.getProvider())
                .amount(pending.getAmount())
                .description(request.getDescription())
                .providerReferenceId(pending.getProviderPaymentId())
                .checkoutUrl(pending.getCheckoutUrl())
                .success(true)
                .message("Existing payment link reused")
                .build();
    }

    @Override
    @Transactional
    public PaymentDTO verifyPayment(final PaymentVerifyRequest request, final UUID callerUserId) {
        if (request.getGateway() == PaymentGateway.VNPAY || (request.getVnpTxnRef() != null && !request.getVnpTxnRef().isBlank())) {
            final Payment payment = this.findByVnpTxnRef(request.getVnpTxnRef());
            this.requireOwner(payment, callerUserId);
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
        this.requireOwner(payment, callerUserId);

        final String status = paymentDetails.optString("status");
        payment.setProviderPaymentId(request.getRazorpayPaymentId());

        if ("captured".equalsIgnoreCase(status)) {
            this.markSuccess(payment, request.getRazorpayPaymentId());
        } else if ("failed".equalsIgnoreCase(status) && payment.getStatus() != PaymentStatus.SUCCESS) {
            this.markFailed(payment, paymentDetails.optString("error_description", "Payment verification failed"));
            log.warn("Payment {} failed for bookingId={}: {}", payment.getId(), payment.getBookingId(), payment.getFailureReason());
        } else {
            // created/authorized: Razorpay chưa xử lý xong, giữ nguyên PENDING để client retry verify
            log.info("Payment {} still in intermediate Razorpay status={}", payment.getId(), status);
        }

        this.paymentRepository.save(payment);
        return PaymentMapper.toDTO(payment);
    }

    // verify do khách tự gọi qua gateway: chỉ chủ của payment mới được xác nhận, kẻ khác biết mã giao dịch cũng không can thiệp được.
    private void requireOwner(final Payment payment, final UUID callerUserId) {
        if (callerUserId == null || !callerUserId.equals(payment.getUserId())) {
            log.warn("User {} tried to verify payment {} owned by another user", callerUserId, payment.getId());
            throw new ForbiddenException("You are not allowed to verify this payment");
        }
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

        // Endpoint công khai nên CHỈ phục vụ kết quả có chữ ký của VNPay: không có kết quả/chữ ký thì không tra cứu
        // (trước đây hỏi theo paymentId/bookingId trả về cả PaymentDTO cho bất kỳ ai). Kiểm tra chữ ký TRƯỚC khi chạm DB
        // để không dò được payment nào tồn tại qua mã lỗi 404 so với 400.
        final String responseCode = params.get("vnp_ResponseCode");
        final String secureHash = params.get("vnp_SecureHash");
        if (responseCode == null || responseCode.isBlank() || secureHash == null || secureHash.isBlank()) {
            throw new BusinessException("VNPay result parameters are required");
        }
        if (!this.vnPayService.verifySignature(onlyVnpFields(params), secureHash)) {
            throw new BusinessException("Invalid VNPay signature");
        }

        final Payment payment = this.findByVnpTxnRef(params.get("vnp_TxnRef"));
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

        this.applyVnpayResult(payment, vnpFields);
        return PaymentMapper.toDTO(payment);
    }

    // Ghi kết quả VNPay (đã kiểm chữ ký, tham chiếu và số tiền) vào payment: dùng chung cho trình duyệt quay lại và IPN.
    private void applyVnpayResult(final Payment payment, final Map<String, String> vnpFields) {
        final String responseCode = vnpFields.get("vnp_ResponseCode");
        final String transactionNo = vnpFields.get("vnp_TransactionNo");
        if (transactionNo != null && !transactionNo.isBlank()) {
            payment.setProviderPaymentId(transactionNo);
        }
        if ("00".equals(responseCode)) {
            this.markSuccess(payment, transactionNo);
        } else {
            this.markFailed(payment, getVNPayErrorMessage(responseCode));
            log.warn("VNPay: Payment {} is FAILED with code: {}", payment.getId(), responseCode);
        }
        this.paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public VnpayIpnResponse handleVnpayIpn(final Map<String, String> params) {
        final Map<String, String> vnpFields = params == null ? Map.of() : onlyVnpFields(params);
        // Chữ ký TRƯỚC khi chạm DB: không dò được payment nào tồn tại qua mã trả về.
        if (!this.vnPayService.verifySignature(vnpFields, vnpFields.get("vnp_SecureHash"))) {
            log.warn("VNPay IPN rejected: invalid signature (txnRef={})", vnpFields.get("vnp_TxnRef"));
            return VnpayIpnResponse.INVALID_SIGNATURE;
        }
        final Payment payment = this.findPaymentByTxnRefOrNull(vnpFields.get("vnp_TxnRef"));
        if (payment == null) {
            log.warn("VNPay IPN: no payment for txnRef={}", vnpFields.get("vnp_TxnRef"));
            return VnpayIpnResponse.ORDER_NOT_FOUND;
        }
        if (!this.amountMatches(payment, vnpFields.get("vnp_Amount"))) {
            log.error("VNPay IPN: amount mismatch for payment {} (vnp_Amount={})", payment.getId(),
                    vnpFields.get("vnp_Amount"));
            return VnpayIpnResponse.INVALID_AMOUNT;
        }
        final boolean succeeded = "00".equals(vnpFields.get("vnp_ResponseCode"));
        // Đã chốt: SUCCESS/REFUNDED/DUPLICATE không bao giờ đổi; FAILED chỉ được đổi nếu VNPay báo thành công muộn.
        final PaymentStatus status = payment.getStatus();
        if (status == PaymentStatus.SUCCESS || status == PaymentStatus.REFUNDED || status == PaymentStatus.DUPLICATE
                || (status == PaymentStatus.FAILED && !succeeded)) {
            return VnpayIpnResponse.ALREADY_CONFIRMED;
        }
        this.applyVnpayResult(payment, vnpFields);
        log.info("VNPay IPN applied to payment {} (bookingId={}, vnp_ResponseCode={})", payment.getId(),
                payment.getBookingId(), vnpFields.get("vnp_ResponseCode"));
        return VnpayIpnResponse.CONFIRMED;
    }

    private Payment findPaymentByTxnRefOrNull(final String txnRef) {
        try {
            return this.paymentRepository.findById(UUID.fromString(txnRef)).orElse(null);
        } catch (final IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    private boolean amountMatches(final Payment payment, final String vnpAmount) {
        if (vnpAmount == null || payment.getAmount() == null) {
            return false;
        }
        try {
            final BigDecimal expected = BigDecimal.valueOf(payment.getAmount()).multiply(BigDecimal.valueOf(100));
            return expected.compareTo(new BigDecimal(vnpAmount)) == 0;
        } catch (final NumberFormatException ex) {
            return false;
        }
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

    // Chuyển sang SUCCESS; sau commit PaymentOutcomePublisher báo booking (Kafka rồi HTTP); gọi lặp lại (đã SUCCESS) thì không báo thêm.
    private void markSuccess(final Payment payment, final String transactionId) {
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.DUPLICATE) {
            return;
        }
        final Payment alreadyPaid = this.paymentRepository.findByBookingIdAndStatusIn(payment.getBookingId(),
                List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED)).stream()
                .filter(other -> !other.getId().equals(payment.getId())).findFirst().orElse(null);
        if (alreadyPaid != null) {
            // Khách trả lần hai cho booking đã thanh toán (hai link, hai tab...). Tiền ĐÃ bị thu nhưng đây không được là payment
            // thành công thứ hai: booking/hoàn tiền giả định một payment trả tiền cho mỗi booking. Không phát event, chờ hoàn tay.
            payment.setStatus(PaymentStatus.DUPLICATE);
            payment.setTransactionId(transactionId);
            payment.setPaidAt(LocalDateTime.now());
            payment.setFailureReason("DUPLICATE PAYMENT: booking already paid by payment " + alreadyPaid.getId()
                    + "; the customer was charged twice and must be refunded manually");
            log.error("DUPLICATE payment {} for bookingId={}: already paid by {}; refund it manually", payment.getId(),
                    payment.getBookingId(), alreadyPaid.getId());
            return;
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidBookingId(payment.getBookingId());
        if (transactionId != null && !transactionId.isBlank()) {
            payment.setTransactionId(transactionId);
        }
        payment.setPaidAt(LocalDateTime.now());
        log.info("Payment {} captured successfully for bookingId={}", payment.getId(), payment.getBookingId());
        this.eventPublisher.publishEvent(new PaymentSucceededEvent(payment.getId(), payment.getBookingId(),
                payment.getUserId(), payment.getAmount(), payment.getTransactionId(), payment.getProviderPaymentId(),
                payment.getPaidAt()));
    }

    // Chuyển sang FAILED và phát PaymentFailedEvent (đẩy lên Kafka SAU KHI commit). Không ai nhả ghế ở đây: booking-service
    // là bên duy nhất chốt/nhả ghế, ghế đang giữ tự hết hạn theo TTL.
    // Gọi lặp lại khi đã FAILED thì không phát thêm.
    private void markFailed(final Payment payment, final String reason) {
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        this.eventPublisher.publishEvent(new PaymentFailedEvent(payment.getId(), payment.getBookingId(),
                payment.getUserId(), payment.getAmount(), payment.getTransactionId(), reason));
    }

    private Payment findByVnpTxnRef(final String txnRef) {
        try {
            return this.paymentRepository.findById(UUID.fromString(txnRef))
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", txnRef));
        } catch (final IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("Invalid VNPay transaction reference");
        }
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
