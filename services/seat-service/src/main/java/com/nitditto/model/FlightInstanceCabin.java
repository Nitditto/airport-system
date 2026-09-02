package com.nitditto.model;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flight_instance_cabins", indexes = {
        @Index(name = "idx_fic_flight_instance", columnList = "flight_instance_id"),
        @Index(name = "idx_fic_cabin_class", columnList = "cabin_class_id")
}, uniqueConstraints = {
        // 1 chuyến bay chỉ có đúng 1 FlightInstanceCabin cho mỗi hạng ghế
        @UniqueConstraint(name = "uk_fic_flight_instance_cabin_class",
                columnNames = {"flight_instance_id", "cabin_class_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightInstanceCabin extends BaseEntity {

    @Column(name = "flight_instance_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightInstanceId;

    @Column(name = "cabin_class_id", columnDefinition = "binary(16)", nullable = false)
    private UUID cabinClassId;

    @Column(nullable = false)
    private Integer totalSeats;

    @Builder.Default
    @Column(nullable = false)
    private Integer bookedSeats = 0;

    @Column(nullable = false)
    private Double baseFare;

    @Builder.Default
    private Double windowSurcharge = 0.0;

    @Builder.Default
    private Double aisleSurcharge = 0.0;

    private Double taxesAndFees;
    private Double airlineFees;

    // Override/khuyến mãi cho cabin này, giống Fare.currentPrice bên pricing-service
    private Double currentPrice;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    public Integer getAvailableSeats() {
        final int total = this.totalSeats != null ? this.totalSeats : 0;
        final int booked = this.bookedSeats != null ? this.bookedSeats : 0;
        return total - booked;
    }

    /**
     * Tổng giá vé của cabin này trên chuyến bay cụ thể (chưa cộng surcharge riêng của từng
     * ghế). Bám đúng công thức {@code Fare.getTotalPrice()} bên pricing-service: current là
     * phần cộng thêm (khuyến mãi/điều chỉnh), không phải giá trị ghi đè base.
     */
    public Double getTotalCabinPrice() {
        final double base = this.baseFare != null ? this.baseFare : 0.0;
        final double taxes = this.taxesAndFees != null ? this.taxesAndFees : 0.0;
        final double fees = this.airlineFees != null ? this.airlineFees : 0.0;
        final double current = this.currentPrice != null ? this.currentPrice : 0.0;
        return base + taxes + fees + current;
    }
}
