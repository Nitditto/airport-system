package com.nitditto.service.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.service.gateway.RazorpayRefundService.Command;
import com.nitditto.service.gateway.VNPayRefundService.Outcome;
import com.nitditto.service.gateway.VNPayRefundService.Result;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RazorpayRefundServiceTest {

  @Mock
  private RazorpayService razorpayService;

  private RazorpayRefundService service;

  @BeforeEach
  void setUp() {
    this.service = new RazorpayRefundService(this.razorpayService);
  }

  private static Command command() {
    return new Command("pay_ABC123", 382_778L, "req-1");
  }

  private void configured() {
    when(this.razorpayService.isConfigured()).thenReturn(true);
  }

  @Test
  void aCreatedRefund_isSuccess_whetherPendingOrProcessed_andCarriesTheRefundId() throws Exception {
    configured();
    when(this.razorpayService.refundPayment("pay_ABC123", 382_778L, "req-1"))
        .thenReturn(new JSONObject().put("id", "rfnd_1").put("status", "pending"));

    final Result result = this.service.refund(command());

    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(result.transactionNo()).isEqualTo("rfnd_1");
  }

  @Test
  void aBusinessErrorFromRazorpay_isRejected_meaningTheCustomerWasNotRefunded() throws Exception {
    configured();
    when(this.razorpayService.refundPayment(anyString(), anyLong(), anyString()))
        .thenThrow(new RazorpayException("BAD_REQUEST_ERROR:The refund amount provided is greater than amount captured"));

    final Result result = this.service.refund(command());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.message()).contains("greater than amount captured");
  }

  @Test
  void anyHttp4xx_isRejected_becauseTheRequestWasNotProcessed() throws Exception {
    configured();
    // "Status Code: 404" là thông báo THẬT của SDK khi Razorpay không tìm thấy payment (kiểm chứng với Razorpay test mode)
    for (final String message : new String[] {"Status Code: 404", "Status Code: 401", "Status Code: 429"}) {
      doThrow(new RazorpayException(message)).when(this.razorpayService)
          .refundPayment(anyString(), anyLong(), anyString());
      assertThat(this.service.refund(command()).outcome()).as(message).isEqualTo(Outcome.REJECTED);
    }
  }

  @Test
  void serverErrors_areUnknown_becauseRazorpayMayHaveProcessedTheRequest() throws Exception {
    configured();
    for (final String message : new String[] {"Status Code: 500", "Status Code: 502", "Status Code: 504"}) {
      doThrow(new RazorpayException(message)).when(this.razorpayService)
          .refundPayment(anyString(), anyLong(), anyString());
      assertThat(this.service.refund(command()).outcome()).as(message).isEqualTo(Outcome.UNKNOWN);
    }
  }

  @Test
  void anyOtherFailure_isUnknown_becauseTheMoneyMayHaveMoved() throws Exception {
    configured();
    when(this.razorpayService.refundPayment(anyString(), anyLong(), anyString()))
        .thenThrow(new RazorpayException("java.net.SocketTimeoutException: Read timed out"));

    assertThat(this.service.refund(command()).outcome()).isEqualTo(Outcome.UNKNOWN);

    // đặt lại stub bằng doThrow: when(...) sẽ gọi chính stub đang ném lỗi
    doThrow(new RazorpayException("GATEWAY_ERROR:The payment could not be processed")).when(this.razorpayService)
        .refundPayment(anyString(), anyLong(), anyString());
    assertThat(this.service.refund(command()).outcome()).isEqualTo(Outcome.UNKNOWN);

    doThrow(new IllegalStateException("boom")).when(this.razorpayService)
        .refundPayment(anyString(), anyLong(), anyString());
    assertThat(this.service.refund(command()).outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void aResponseWithoutAnId_orAFailedRefund_isUnknown() throws Exception {
    configured();
    when(this.razorpayService.refundPayment(anyString(), anyLong(), anyString())).thenReturn(new JSONObject());
    assertThat(this.service.refund(command()).outcome()).isEqualTo(Outcome.UNKNOWN);

    when(this.razorpayService.refundPayment(anyString(), anyLong(), anyString()))
        .thenReturn(new JSONObject().put("id", "rfnd_2").put("status", "failed"));
    assertThat(this.service.refund(command()).outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void notConfigured_orWithoutAPaymentId_isRejectedWithoutCallingRazorpay() throws Exception {
    when(this.razorpayService.isConfigured()).thenReturn(false);
    assertThat(this.service.refund(command()).outcome()).isEqualTo(Outcome.REJECTED);

    when(this.razorpayService.isConfigured()).thenReturn(true);
    assertThat(this.service.refund(new Command(" ", 100L, "r")).outcome()).isEqualTo(Outcome.REJECTED);

    verify(this.razorpayService, never()).refundPayment(anyString(), anyLong(), anyString());
  }
}
