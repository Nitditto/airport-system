package com.nitditto.repository.spec;

import com.nitditto.enums.BookingStatus;
import com.nitditto.model.Booking;
import com.nitditto.model.Passenger;

import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class BookingSpecs {

    private BookingSpecs() {
    }

    public static Specification<Booking> airlineId(final UUID airlineId) {
        return (root, query, cb) ->
                airlineId == null ? null : cb.equal(root.get("airlineId"), airlineId);
    }

    public static Specification<Booking> status(final BookingStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("bookingStatus"), status);
    }

    public static Specification<Booking> flightInstanceId(final UUID flightInstanceId) {
        return (root, query, cb) ->
                flightInstanceId == null ? null : cb.equal(root.get("flightInstanceId"), flightInstanceId);
    }

    // Không còn association Booking<->Passenger nên "tìm theo tên/email/sđt hành khách"
    // phải qua subquery đối chiếu Passenger.bookingId thay vì join.
    public static Specification<Booking> search(final String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            final String pattern = "%" + search.toLowerCase() + "%";

            final Subquery<UUID> passengerMatch = query.subquery(UUID.class);
            final var passenger = passengerMatch.from(Passenger.class);
            passengerMatch.select(passenger.get("bookingId")).where(cb.or(
                    cb.like(cb.lower(passenger.get("firstName")), pattern),
                    cb.like(cb.lower(passenger.get("lastName")), pattern),
                    cb.like(cb.lower(passenger.get("email")), pattern),
                    cb.like(cb.lower(passenger.get("phone")), pattern)));

            return cb.or(
                    cb.like(cb.lower(root.get("bookingReference")), pattern),
                    root.get("id").in(passengerMatch));
        };
    }
}
