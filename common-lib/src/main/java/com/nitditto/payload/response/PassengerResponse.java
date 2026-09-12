package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.nitditto.enums.Gender;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerResponse {
    private UUID id;
    private UUID bookingId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;

    private String nationality;

    private UUID primaryUserId;
    private String primaryUserName;

    private Boolean isActive;
    private Integer age;
    private Boolean isAdult;
    private String fullName;

    private Instant createdAt;
    private Instant updatedAt;
}
