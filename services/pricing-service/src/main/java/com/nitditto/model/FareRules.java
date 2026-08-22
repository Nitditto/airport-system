package com.nitditto.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareRules {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String ruleName;

    private Long airlineId;

    @OneToOne
    private Fare fare;

    private Boolean isRefundable;

    private Double changeFee;

    private Double cancellationFee;

    private Integer refundDeadlineDays;

    private Integer changeDeadlineHours;

    private Boolean isChangeable;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}
