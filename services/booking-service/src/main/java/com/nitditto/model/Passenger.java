package com.nitditto.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.Gender;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "passengers", indexes = {
        @Index(name = "idx_passenger_booking_id", columnList = "booking_id"),
        @Index(name = "idx_passenger_primary_user_id", columnList = "primary_user_id")
})
public class Passenger extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String email;
    private String phone;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String nationality;

    @Column(name = "primary_user_id", columnDefinition = "binary(16)")
    private UUID primaryUserId;

    @Column(name = "booking_id", columnDefinition = "binary(16)", nullable = false)
    private UUID bookingId;

    @Builder.Default
    private Boolean isActive = true;

    @Version
    private Long version;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public boolean isAdult() {
        return getAge() >= 18;
    }
}
