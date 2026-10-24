package com.nitditto.service.impl;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.AncillaryClient;
import com.nitditto.client.FlightClient;
import com.nitditto.client.PaymentClient;
import com.nitditto.client.SeatClient;
import com.nitditto.enums.BookingStatus;
import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.BookingEnrichmentContext;
import com.nitditto.mapper.BookingMapper;
import com.nitditto.model.Booking;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.BookingResponse;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.payload.response.TicketResponse;
import com.nitditto.repository.BookingRepository;
import com.nitditto.repository.spec.BookingSpecs;
import com.nitditto.service.BookingConfirmationService;
import com.nitditto.service.BookingEnrichmentService;
import com.nitditto.service.BookingPriceCalculator;
import com.nitditto.service.BookingService;
import com.nitditto.service.PassengerService;
import com.nitditto.service.SeatReservationService;
import com.nitditto.service.TicketService;
import com.nitditto.service.integration.FareIntegrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final PassengerService passengerService;
    private final TicketService ticketService;
    private final FlightClient flightClient;
    private final SeatClient seatClient;
    private final AncillaryClient ancillaryClient;
    private final PaymentClient paymentClient;
    private final FareIntegrationService fareIntegrationService;
    private final AirlineClient airlineClient;
    private final SeatReservationService seatReservationService;
    private final BookingEnrichmentService bookingEnrichmentService;
    private final BookingConfirmationService bookingConfirmationService;

    @Override
    @Transactional
    public PaymentInitiateResponse createBooking(final BookingRequest request, final UUID userId) {
        final List<UUID> seatInstanceIds = request.getPassengers().stream()
                .map(PassengerRequest::getSeatInstanceId)
                .collect(Collectors.toList());
        if (seatInstanceIds.stream().distinct().count() != seatInstanceIds.size()) {
            throw new BusinessException("Each passenger must be assigned a different seat");
        }

        // Giữ ghế TRƯỚC khi tạo booking: nếu ghế đã bị người khác giữ/đặt thì dừng ngay, không tạo đơn và không thu tiền.
        this.seatReservationService.hold(userId, seatInstanceIds);
        try {
            return this.createBookingAndInitiatePayment(request, userId, seatInstanceIds);
        } catch (final RuntimeException ex) {
            this.seatReservationService.releaseQuietly(userId, seatInstanceIds);
            throw ex;
        }
    }

    private PaymentInitiateResponse createBookingAndInitiatePayment(final BookingRequest request,
            final UUID userId, final List<UUID> seatInstanceIds) {
        final String bookingReference = generateBookingReference();
        final FlightResponse flightResponse = flightClient.getFlightById(request.getFlightId())
                .getBody().getData();

        Booking booking = BookingMapper.toEntity(request, userId, bookingReference, seatInstanceIds);
        booking.setAirlineId(flightResponse.getAirline().getId());
        booking = this.bookingRepository.saveAndFlush(booking);

        final List<PassengerResponse> passengers = this.passengerService
                .createPassengers(request.getPassengers(), booking.getId(), userId);
        enrichTickets(this.ticketService.generateTicketsForBooking(booking.getId(), passengers), booking, passengers);

        final double totalPrice = this.calculateTotalPrice(request, booking, seatInstanceIds);
        final PaymentGateway gateway = request.getPaymentGateway() != null
                ? request.getPaymentGateway()
                : PaymentGateway.VNPAY;

        final PaymentInitiateRequest paymentInitiateRequest = PaymentInitiateRequest.builder()
                .userId(userId)
                .bookingId(booking.getId())
                .amount(totalPrice)
                .paymentGateway(gateway)
                .description("Payment for booking ID : " + bookingReference)
                .build();

        return paymentClient.initiatePayment(paymentInitiateRequest).getBody().getData();
    }

    private double calculateTotalPrice(final BookingRequest request, final Booking booking,
            final List<UUID> seatInstanceIds) {
        final Double farePerPassenger = this.fareIntegrationService.calculateFareTotal(request.getFareId());
        final Double seatPrice = this.seatClient.calculateSeatPrice(seatInstanceIds);
        final Double ancillaryPrice = this.ancillaryClient.calculateAncillariesPrice(booking.getAncillaryIds()).getData();
        final Double mealPrice = this.ancillaryClient.calculateMealPrice(booking.getMealIds()).getData();
        return BookingPriceCalculator.total(farePerPassenger, request.getPassengers().size(), seatPrice,
                ancillaryPrice, mealPrice);
    }

    @Override
    public BookingResponse getBookingById(final UUID id) {
        final Booking booking = this.bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
        return convertToBookingResponses(List.of(booking)).get(0);
    }

    @Override
    public BookingResponse confirmPayment(final UUID id) {
        // Chạy trong transaction riêng và commit trước khi đọc lại, nên bản đọc bên dưới thấy trạng thái mới.
        this.bookingConfirmationService.confirmPayment(id);
        return this.getBookingById(id);
    }

    @Override
    public Page<BookingResponse> getAllBookingsByAirline(final UUID userId, final String search,
            final BookingStatus status, final UUID flightInstanceId, final Pageable pageable) {

        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();

        final Specification<Booking> spec = Specification
                .where(BookingSpecs.airlineId(airlineResponse.getId()))
                .and(BookingSpecs.status(status))
                .and(BookingSpecs.flightInstanceId(flightInstanceId))
                .and(BookingSpecs.search(search));
        return mapPageToResponses(this.bookingRepository.findAll(spec, pageable));
    }

    @Override
    public Page<BookingResponse> getBookingsByUser(final UUID userId, final Pageable pageable) {
        return mapPageToResponses(this.bookingRepository.findByUserId(userId, pageable));
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(final UUID id, final UUID requesterUserId) {
        final Booking booking = this.bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
        requireOwner(booking, requesterUserId, "cancel");
        final BookingStatus previous = booking.getBookingStatus();

        booking.setBookingStatus(BookingStatus.CANCELLED);
        final Booking updated = this.bookingRepository.save(booking);
        this.ticketService.cancelTicketsForBooking(id);
        this.releaseSeatsOf(booking, previous);

        // todo: khi có payment-service, gọi refund/void payment ở đây

        return convertToBookingResponses(List.of(updated)).get(0);
    }

    // Đơn đã xác nhận: ghế đã bán nên phải trả về trống. Đơn còn PENDING: chỉ nhả hold của chính người đặt (ghế có thể
    // đã được người khác giữ sau khi hold hết hạn, không được đụng vào).
    private void releaseSeatsOf(final Booking booking, final BookingStatus previous) {
        if (booking.getSeatInstanceIds() == null || booking.getSeatInstanceIds().isEmpty()) {
            return;
        }
        if (previous == BookingStatus.CONFIRMED) {
            this.seatReservationService.freeQuietly(booking.getSeatInstanceIds());
        } else {
            this.seatReservationService.releaseQuietly(booking.getUserId(), booking.getSeatInstanceIds());
        }
    }

    @Override
    @Transactional
    public void deleteBooking(final UUID id, final UUID requesterUserId) {
        final Booking booking = this.bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
        requireOwner(booking, requesterUserId, "delete");

        // Không còn cascade -> xoá con trước, cha sau, cùng 1 transaction.
        this.ticketService.deleteTicketsForBooking(id);
        this.passengerService.deletePassengersForBooking(id);
        this.bookingRepository.deleteById(id);
    }

    private void requireOwner(final Booking booking, final UUID requesterUserId, final String action) {
        if (!booking.getUserId().equals(requesterUserId)) {
            throw new BusinessException("You are not allowed to " + action + " this booking");
        }
    }

    private String generateBookingReference() {
        String bookingReference;
        do {
            bookingReference = "BK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (this.bookingRepository.existsByBookingReference(bookingReference));
        return bookingReference;
    }

    private Page<BookingResponse> mapPageToResponses(final Page<Booking> page) {
        final List<BookingResponse> responses = convertToBookingResponses(page.getContent());
        return new PageImpl<>(responses, page.getPageable(), page.getTotalElements());
    }

    // Công thức batch-load chuẩn: gom distinct id -> 1 lời gọi/query IN cho mỗi loại dữ liệu -> Map -> ghép trong bộ nhớ.
    // Số lời gọi là hằng số theo số loại dữ liệu, không phụ thuộc số booking.
    private List<BookingResponse> convertToBookingResponses(final List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        final List<UUID> bookingIds = bookings.stream().map(Booking::getId).distinct().toList();

        final Map<UUID, List<PassengerResponse>> passengersByBookingId = this.passengerService
                .findByBookingIds(bookingIds);
        final Map<UUID, List<TicketResponse>> ticketsByBookingId = this.ticketService.findByBookingIds(bookingIds);
        for (final Booking booking : bookings) {
            enrichTickets(ticketsByBookingId.getOrDefault(booking.getId(), List.of()), booking,
                    passengersByBookingId.getOrDefault(booking.getId(), List.of()));
        }

        final Map<UUID, BookingEnrichmentContext> contexts = this.bookingEnrichmentService
                .load(bookings, passengersByBookingId, ticketsByBookingId);

        final List<BookingResponse> responses = new ArrayList<>(bookings.size());
        for (final Booking booking : bookings) {
            final BookingEnrichmentContext context = contexts.get(booking.getId());
            this.reconcile(booking, context);
            responses.add(BookingMapper.toResponse(booking, context));
        }
        return responses;
    }

    // Tự chữa cho đơn còn PENDING: đã có thanh toán SUCCESS mà chưa được xác nhận (webhook từ payment-service bị lỡ,
    // hoặc đơn cũ trước khi có webhook) thì xác nhận ngay; quá hạn thanh toán thì chuyển EXPIRED. Việc ghi nằm trong
    // transaction riêng của BookingConfirmationService; ở đây chỉ cập nhật trạng thái để response phản ánh đúng.
    private void reconcile(final Booking booking, final BookingEnrichmentContext context) {
        if (booking.getBookingStatus() != BookingStatus.PENDING) {
            return;
        }
        try {
            final boolean paid = context.payment() != null
                    && context.payment().getPaymentStatus() == PaymentStatus.SUCCESS;
            final BookingStatus status = paid
                    ? this.bookingConfirmationService.confirmPayment(booking.getId())
                    : this.bookingConfirmationService.expireIfStale(booking.getId());
            booking.setBookingStatus(status);
        } catch (final RuntimeException ex) {
            log.warn("Could not reconcile booking {}: {}", booking.getBookingReference(), ex.getMessage());
        }
    }

    // TicketMapper không được phép tự đi lấy Booking/Passenger (rule NO-JPA-relationship kéo theo mapper thuần không
    // traversal) -> điền field phẳng denormalized ở đây, nơi đã có sẵn cả Booking lẫn danh sách Passenger tương ứng.
    private List<TicketResponse> enrichTickets(final List<TicketResponse> tickets, final Booking booking,
            final List<PassengerResponse> passengers) {
        final Map<UUID, PassengerResponse> passengerById = passengers.stream()
                .collect(Collectors.toMap(PassengerResponse::getId, Function.identity()));
        for (final TicketResponse ticket : tickets) {
            ticket.setBookingReference(booking.getBookingReference());
            final PassengerResponse passenger = passengerById.get(ticket.getPassengerId());
            if (passenger != null) {
                ticket.setPassengerFirstName(passenger.getFirstName());
                ticket.setPassengerLastName(passenger.getLastName());
                ticket.setPassengerEmail(passenger.getEmail());
            }
        }
        return tickets;
    }
}
