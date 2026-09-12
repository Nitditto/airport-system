package com.nitditto.payload.request;

import java.time.LocalDate;
import java.util.UUID;

import com.nitditto.enums.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PassengerRequest {

  @NotBlank(message = "First name is required")
  private String firstName;

  @NotBlank(message = "Last name is required")
  private String lastName;

  @NotBlank(message = "Email is required")
  @Email(message = "Email should be valid")
  private String email;

  @NotBlank(message = "Phone number is required")
  @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be a 10-digit number")
  private String phone;

  @NotNull(message = "Date of birth is required")
  @Past(message = "Date of birth should be in past")
  private LocalDate dateOfBirth;

  @NotNull(message = "Gender is required")
  private Gender gender;

  @NotNull(message = "Seat Instance ID is required")
  private UUID seatInstanceId;

  private String nationality;

}
