package com.nitditto.model;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.CoverageType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "insurance_coverages", indexes = {
        @Index(name = "idx_insurance_coverage_ancillary", columnList = "ancillary_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceCoverage extends BaseEntity {

    @Column(name = "ancillary_id", columnDefinition = "binary(16)", nullable = false)
    private UUID ancillaryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoverageType coverageType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double coverageAmount;

    @Column(length = 100)
    private String emergencyContact;

    private boolean isFlat = true;

    @Column(length = 500)
    private String claimCondition;

    private Integer displayOrder;
    private boolean active = true;
}
