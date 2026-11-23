package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nitditto.controller.PaymentController;
import com.nitditto.exception.GlobalExceptionHandler;
import com.nitditto.service.PaymentService;
import com.nitditto.service.RefundService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** GET /api/payments: sortBy chỉ nhận trường cho phép, size bị kẹp về [1, 100], page âm bị từ chối. */
@ExtendWith(MockitoExtension.class)
class PaymentListPagingTest {

  @Mock
  private PaymentService paymentService;
  @Mock
  private RefundService refundService;

  private MockMvc mockMvc;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(this.paymentService, this.refundService))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test
  void unknownSortField_returns400_notServerError() throws Exception {
    this.mockMvc.perform(get("/api/payments").param("sortBy", "password").header("X-User-Id", this.userId))
        .andExpect(status().isBadRequest());

    verify(this.paymentService, never()).getAllPayments(any(), any());
  }

  @Test
  void hugeSize_isClampedTo100() throws Exception {
    when(this.paymentService.getAllPayments(eq(this.userId), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    this.mockMvc.perform(get("/api/payments").param("size", "1000000").param("sortBy", "amount")
            .param("sortDirection", "ASC").header("X-User-Id", this.userId))
        .andExpect(status().isOk());

    final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(this.paymentService).getAllPayments(eq(this.userId), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    assertThat(pageable.getValue().getSort().getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void negativePage_returns400() throws Exception {
    this.mockMvc.perform(get("/api/payments").param("page", "-1").header("X-User-Id", this.userId))
        .andExpect(status().isBadRequest());
  }
}
