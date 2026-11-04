package com.nitditto.event;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@Builder 
@NoArgsConstructor 
@AllArgsConstructor 
public class PaymentCompletedEvent {
  
  private UUID paymentId;
  private UUID bookingId;
  private UUID userId;
  private Double amount;
  private String transactionId;
  private String providerPaymentId;
  private LocalDateTime paidAt;
}
