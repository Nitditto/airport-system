package com.nitditto.model;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.CabinClassType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "cabin_classes", uniqueConstraints = {
        // Một tàu bay chỉ có tối đa 1 hạng ghế mỗi loại, và mỗi code chỉ dùng 1 lần trên tàu bay đó.
        // Cả 2 constraint đều bắt đầu bằng aircraft_id nên đã phục vụ luôn findByAircraftId
        // (leftmost prefix) -> không cần khai thêm index riêng cho aircraft_id.
        @UniqueConstraint(name = "uk_cabin_aircraft_name", columnNames = {"aircraft_id", "name"}),
        @UniqueConstraint(name = "uk_cabin_aircraft_code", columnNames = {"aircraft_id", "code"})
})

public class CabinClass extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CabinClassType name;

    @Column(nullable = false)
    private String code;

    private String description;

    @Column(name = "aircraft_id", nullable = false, columnDefinition = "binary(16)")
    private UUID aircraftId;

    @Builder.Default
    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isBookable = true;

    private Integer typicalSeatPitch;
    private Integer typicalSeatWidth;
    private String seatType;

}
