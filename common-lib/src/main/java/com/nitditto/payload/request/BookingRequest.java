package com.nitditto.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

import com.nitditto.embeddable.ContactInfo;
import com.nitditto.enums.CabinClassType;
import com.nitditto.enums.PaymentGateway;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotNull(message = "Flight ID is required")
    private UUID flightId;

    @NotNull(message = "Flight Instance ID is required")
    private UUID flightInstanceId;

    @NotNull(message = "Cabin class type is required")
    private CabinClassType cabinClassType;

    @NotNull(message = "Fare ID is required")
    private UUID fareId;

    @Valid
    @NotNull(message = "At least one passenger is required")
    @Size(min = 1, message = "At least one passenger is required")
    private List<PassengerRequest> passengers;

    private ContactInfo contactInfo;

    private List<UUID> ancillaryIds;

    private List<UUID> mealIds;

    @NotNull(message = "Payment gateway is required")
    private PaymentGateway paymentGateway;
}
