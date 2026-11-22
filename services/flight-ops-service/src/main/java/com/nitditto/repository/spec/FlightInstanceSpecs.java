package com.nitditto.repository.spec;

import com.nitditto.enums.FlightStatus;
import com.nitditto.model.FlightInstance;
import jakarta.persistence.criteria.Expression;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class FlightInstanceSpecs {

  private FlightInstanceSpecs() {
  }

  public static Specification<FlightInstance> openForSale() {
    return (root, query, cb) -> cb.and(
        cb.isTrue(root.get("isActive")),
        cb.equal(root.get("status"), FlightStatus.SCHEDULED));
  }

  public static Specification<FlightInstance> route(final UUID departureAirportId, final UUID arrivalAirportId) {
    return (root, query, cb) -> cb.and(
        cb.equal(root.get("departureAirportId"), departureAirportId),
        cb.equal(root.get("arrivalAirportId"), arrivalAirportId));
  }

  /** Khởi hành trong ngày {@code date} và sau {@code now} (giờ địa phương của sân bay đi). */
  public static Specification<FlightInstance> departsOnDateAfter(final LocalDate date, final LocalDateTime now) {
    final LocalDateTime dayStart = date.atStartOfDay();
    final LocalDateTime earliest = now.isAfter(dayStart) ? now : dayStart;
    return (root, query, cb) -> cb.and(
        cb.greaterThanOrEqualTo(root.get("departureDateTime"), dayStart),
        cb.lessThan(root.get("departureDateTime"), date.plusDays(1).atStartOfDay()),
        cb.greaterThan(root.get("departureDateTime"), earliest));
  }

  public static Specification<FlightInstance> hasSeats(final Integer passengers) {
    return (root, query, cb) -> passengers == null ? null
        : cb.greaterThanOrEqualTo(root.get("availableSeats"), passengers);
  }

  public static Specification<FlightInstance> airlineIn(final Collection<UUID> airlineIds) {
    return (root, query, cb) -> airlineIds == null || airlineIds.isEmpty() ? null
        : root.get("airlineId").in(airlineIds);
  }

  /** Khoảng giờ: morning 6-11, afternoon 12-17, evening 18-20, night 21-5. {@code any}/rỗng/lạ = không lọc. */
  public static Specification<FlightInstance> hourRange(final String field, final String range) {
    return (root, query, cb) -> {
      if (range == null) {
        return null;
      }
      final Expression<Integer> hour = cb.function("hour", Integer.class, root.get(field));
      return switch (range.trim().toLowerCase()) {
        case "morning" -> cb.between(hour, 6, 11);
        case "afternoon" -> cb.between(hour, 12, 17);
        case "evening" -> cb.between(hour, 18, 20);
        case "night" -> cb.or(cb.greaterThanOrEqualTo(hour, 21), cb.lessThanOrEqualTo(hour, 5));
        default -> null;
      };
    };
  }
}
