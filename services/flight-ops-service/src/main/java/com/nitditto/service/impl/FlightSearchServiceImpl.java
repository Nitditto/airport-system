package com.nitditto.service.impl;

import com.nitditto.client.LocationClient;
import com.nitditto.client.PricingClient;
import com.nitditto.client.SeatClient;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightInstance;
import com.nitditto.payload.request.FlightSearchRequest;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.CabinClassResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.repository.spec.FlightInstanceSpecs;
import com.nitditto.service.FlightSearchService;
import com.nitditto.service.integration.FlightInstanceAssembler;
import com.nitditto.service.integration.ReferenceDataLoader;
import feign.FeignException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Tìm chuyến bay có lọc/sắp xếp theo giá. Giá nằm ở pricing-service nên không lọc được trong DB: DB lọc mọi thứ có
 * thể (tuyến, ngày, ghế, hãng, khung giờ) và lấy tối đa {@link #MAX_CANDIDATES} chuyến; giá, thời lượng, alliance,
 * sắp xếp và phân trang làm trong bộ nhớ để {@code totalElements} đúng. Số lời gọi service khác là hằng số theo
 * số loại dữ liệu (CLAUDE.md §7), không theo số chuyến.
 *
 * <p>Không dùng {@code @Transactional}: các lời gọi Feign không được giữ connection DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchServiceImpl implements FlightSearchService {

  static final int MAX_CANDIDATES = 200;
  private static final int MAX_PAGE_SIZE = 100;

  private final FlightInstanceRepository flightInstanceRepository;
  private final FlightRepository flightRepository;
  private final LocationClient locationClient;
  private final SeatClient seatClient;
  private final PricingClient pricingClient;
  private final ReferenceDataLoader referenceDataLoader;
  private final FlightInstanceAssembler flightInstanceAssembler;

  /** Chuyến ứng viên đã có fare của hạng ghế được chọn. */
  private record Candidate(FlightInstance instance, FareResponse fare) {
  }

  @Override
  public Page<FlightInstanceResponse> searchFlights(final FlightSearchRequest request, final Pageable pageable) {
    this.validate(request);
    final Pageable safePageable = PageRequest.of(pageable.getPageNumber(),
        Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));

    final List<FlightInstance> instances = this.findCandidates(request);
    if (instances.isEmpty()) {
      return Page.empty(safePageable);
    }

    final Map<UUID, Flight> flightById = this.flightRepository
        .findAllById(instances.stream().map(FlightInstance::getFlightId).distinct().toList()).stream()
        .collect(Collectors.toMap(Flight::getId, Function.identity()));

    List<Candidate> candidates = this.attachLowestFares(request, instances, flightById);
    candidates = this.filterInMemory(request, candidates);
    if (candidates.isEmpty()) {
      return Page.empty(safePageable);
    }
    candidates.sort(comparator(request.getSortBy(), request.getSortOrder()));

    final List<Candidate> pageItems = slice(candidates, safePageable);
    final Map<UUID, FlightInstanceResponse> responseById = this.flightInstanceAssembler.assemble(
        pageItems.stream().map(Candidate::instance).toList(), flightById);

    final List<FlightInstanceResponse> content = new ArrayList<>(pageItems.size());
    for (final Candidate candidate : pageItems) {
      final FlightInstanceResponse response = responseById.get(candidate.instance().getId());
      response.setFare(candidate.fare());
      content.add(response);
    }
    return new PageImpl<>(content, safePageable, candidates.size());
  }

  private void validate(final FlightSearchRequest request) {
    if (request.getMinPrice() != null && request.getMaxPrice() != null
        && request.getMinPrice() > request.getMaxPrice()) {
      throw new BusinessException("minPrice must not be greater than maxPrice");
    }
    if (request.getDepartureAirportId().equals(request.getArrivalAirportId())) {
      throw new BusinessException("Departure and arrival airports must be different");
    }
  }

  private List<FlightInstance> findCandidates(final FlightSearchRequest request) {
    // Giờ khởi hành lưu theo giờ địa phương sân bay đi nên "đã cất cánh chưa" phải so theo múi giờ đó.
    final LocalDateTime now = LocalDateTime.now(this.resolveZone(request.getDepartureAirportId()));

    final Specification<FlightInstance> spec = Specification
        .where(FlightInstanceSpecs.openForSale())
        .and(FlightInstanceSpecs.route(request.getDepartureAirportId(), request.getArrivalAirportId()))
        .and(FlightInstanceSpecs.departsOnDateAfter(request.getDepartureDate(), now))
        .and(FlightInstanceSpecs.hasSeats(request.getPassengers()))
        .and(FlightInstanceSpecs.airlineIn(request.getAirlines()))
        .and(FlightInstanceSpecs.hourRange("departureDateTime", request.getDepartureTimeRange()))
        .and(FlightInstanceSpecs.hourRange("arrivalDateTime", request.getArrivalTimeRange()));

    final Page<FlightInstance> page = this.flightInstanceRepository.findAll(spec,
        PageRequest.of(0, MAX_CANDIDATES, Sort.by("departureDateTime")));
    if (page.getTotalElements() > MAX_CANDIDATES) {
      log.warn("Flight search truncated: {} instances for route {}->{} on {}, using first {}",
          page.getTotalElements(), request.getDepartureAirportId(), request.getArrivalAirportId(),
          request.getDepartureDate(), MAX_CANDIDATES);
    }
    return page.getContent();
  }

  private ZoneId resolveZone(final UUID departureAirportId) {
    try {
      final AirportResponse airport = this.locationClient.getAirportById(departureAirportId).getData();
      return airport != null && airport.getTimeZone() != null ? airport.getTimeZone() : ZoneId.systemDefault();
    } catch (final FeignException.NotFound ex) {
      throw new ResourceNotFoundException("Airport", "id", departureAirportId);
    }
  }

  /** Một call seat-service cho mọi máy bay, rồi mỗi hạng ghế một call pricing-service; chuyến thiếu hạng/fare bị loại. */
  private List<Candidate> attachLowestFares(final FlightSearchRequest request, final List<FlightInstance> instances,
      final Map<UUID, Flight> flightById) {
    final List<UUID> aircraftIds = flightById.values().stream().map(Flight::getAircraftId).distinct().toList();
    final Map<UUID, CabinClassResponse> cabinByAircraftId = this.seatClient
        .getBookableCabinClasses(request.getCabinClass(), aircraftIds).getData();

    final Map<UUID, List<UUID>> flightIdsByCabinId = new HashMap<>();
    for (final Flight flight : flightById.values()) {
      final CabinClassResponse cabin = cabinByAircraftId.get(flight.getAircraftId());
      if (cabin != null) {
        flightIdsByCabinId.computeIfAbsent(cabin.getId(), key -> new ArrayList<>()).add(flight.getId());
      }
    }

    final Map<UUID, FareResponse> fareByFlightId = new HashMap<>();
    for (final Map.Entry<UUID, List<UUID>> entry : flightIdsByCabinId.entrySet()) {
      // Số vòng lặp = số hạng ghế khác nhau (thường 1-3), không phải số chuyến.
      final Map<UUID, FareResponse> fares = this.pricingClient
          .getLowestFarePerFlight(entry.getValue(), entry.getKey()).getData();
      if (fares != null) {
        fareByFlightId.putAll(fares);
      }
    }

    final List<Candidate> candidates = new ArrayList<>(instances.size());
    for (final FlightInstance instance : instances) {
      final FareResponse fare = fareByFlightId.get(instance.getFlightId());
      if (fare != null) {
        candidates.add(new Candidate(instance, fare));
      }
    }
    return candidates;
  }

  private List<Candidate> filterInMemory(final FlightSearchRequest request, final List<Candidate> candidates) {
    final Map<UUID, AirlineResponse> airlineById = this.loadAirlinesForAlliance(request, candidates);
    final List<Candidate> result = new ArrayList<>(candidates.size());
    for (final Candidate candidate : candidates) {
      final Double price = candidate.fare().getTotalPrice();
      if (request.getMinPrice() != null && (price == null || price < request.getMinPrice())) {
        continue;
      }
      if (request.getMaxPrice() != null && (price == null || price > request.getMaxPrice())) {
        continue;
      }
      if (request.getMaxDuration() != null && durationMinutes(candidate.instance()) > request.getMaxDuration()) {
        continue;
      }
      if (airlineById != null && !matchesAlliance(request.getAlliance(), airlineById.get(
          candidate.instance().getAirlineId()))) {
        continue;
      }
      result.add(candidate);
    }
    return result;
  }

  /** Chỉ nạp hãng khi có lọc alliance (một call batch); {@code null} nghĩa là không cần lọc. */
  private Map<UUID, AirlineResponse> loadAirlinesForAlliance(final FlightSearchRequest request,
      final List<Candidate> candidates) {
    if (request.getAlliance() == null || request.getAlliance().isBlank()) {
      return null;
    }
    return this.referenceDataLoader.load(
        candidates.stream().map(candidate -> candidate.instance().getAirlineId()).toList(), List.of(), List.of())
        .airlines();
  }

  private static boolean matchesAlliance(final String alliance, final AirlineResponse airline) {
    return airline != null && airline.getAlliance() != null && airline.getAlliance().equalsIgnoreCase(alliance.trim());
  }

  private static long durationMinutes(final FlightInstance instance) {
    if (instance.getDepartureDateTime() == null || instance.getArrivalDateTime() == null) {
      return Long.MAX_VALUE;
    }
    return Duration.between(instance.getDepartureDateTime(), instance.getArrivalDateTime()).toMinutes();
  }

  private static Comparator<Candidate> comparator(final String sortBy, final String sortOrder) {
    final String key = sortBy == null || sortBy.isBlank() ? "departure" : sortBy.trim().toLowerCase();
    final Comparator<Candidate> byDeparture = Comparator.comparing(
        (Candidate c) -> c.instance().getDepartureDateTime(), Comparator.nullsLast(Comparator.naturalOrder()));
    final Comparator<Candidate> primary = switch (key) {
      case "price" -> Comparator.comparing(
          (Candidate c) -> c.fare().getTotalPrice(), Comparator.nullsLast(Comparator.naturalOrder()));
      case "duration" -> Comparator.comparingLong(c -> durationMinutes(c.instance()));
      case "arrival" -> Comparator.comparing(
          (Candidate c) -> c.instance().getArrivalDateTime(), Comparator.nullsLast(Comparator.naturalOrder()));
      case "departure" -> byDeparture;
      default -> throw new BusinessException("Unsupported sortBy: " + sortBy);
    };
    final boolean descending = sortOrder != null && switch (sortOrder.trim().toLowerCase()) {
      case "desc" -> true;
      case "asc", "" -> false;
      default -> throw new BusinessException("Unsupported sortOrder: " + sortOrder);
    };
    // Giờ khởi hành làm tiêu chí phụ để thứ tự ổn định giữa các trang.
    return (descending ? primary.reversed() : primary).thenComparing(byDeparture);
  }

  private static <T> List<T> slice(final List<T> items, final Pageable pageable) {
    final long offset = pageable.getOffset();
    if (offset >= items.size()) {
      return List.of();
    }
    return items.subList((int) offset, (int) Math.min(offset + pageable.getPageSize(), items.size()));
  }
}
