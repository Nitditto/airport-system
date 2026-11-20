package com.nitditto.service;

import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.request.PaymentVerifyRequest;
import com.nitditto.payload.response.PaymentGatewayResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    /**
     * @param clientIp giá trị header X-Forwarded-For do gateway ghi (IP thật của khách); null/không hợp lệ thì dùng
     *                 {@code ClientIp.FALLBACK}
     */
    PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request, String clientIp);

    default PaymentInitiateResponse initiatePayment(final PaymentInitiateRequest request) {
        return this.initiatePayment(request, null);
    }

    PaymentDTO verifyPayment(PaymentVerifyRequest request, UUID callerUserId);

    Page<PaymentDTO> getAllPayments(UUID userId, Pageable pageable);

    Map<UUID, PaymentDTO> getPaymentsByBookingIds(List<UUID> bookingIds);

    PaymentDTO checkPaymentVNPay(Map<String, String> allParams);

    List<PaymentGatewayResponse> getAvailableGateways();

    /**
     * IPN (server-to-server) của VNPay: cập nhật payment theo kết quả có chữ ký dù khách có quay lại trang kết quả hay không.
     * Không ném lỗi nghiệp vụ: mọi trường hợp trả về mã RspCode để VNPay quyết định có gọi lại không.
     */
    com.nitditto.service.gateway.VnpayIpnResponse handleVnpayIpn(java.util.Map<String, String> params);

    /**
     * Áp kết quả tra cứu VNPay (job đối soát) vào một payment còn {@code PENDING}. Chạy trong transaction riêng cho từng
     * payment; payment đã chốt thì bỏ qua.
     */
    void applyVnpayQuery(java.util.UUID paymentId, com.nitditto.service.gateway.VNPayQueryService.Result result);
}
