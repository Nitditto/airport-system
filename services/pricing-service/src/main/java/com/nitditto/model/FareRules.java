package com.nitditto.model;

import jakarta.persistence.*;
import lombok.*;

import com.nitditto.entity.BaseEntity;
import java.util.UUID;

@Entity
@Table(name = "fare_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareRules extends BaseEntity{

    private String ruleName;

    @Column(name = "airline_id", columnDefinition = "binary(16)")
    private UUID airlineId;

    @Column(name = "fare_id", columnDefinition = "binary(16)", nullable = false, unique = true)
    private UUID fareId;

    private Boolean isRefundable;

    private Double changeFee;

    private Double cancellationFee;

    private Integer refundDeadlineDays;

    private Integer changeDeadlineHours;

    private Boolean isChangeable;
}
