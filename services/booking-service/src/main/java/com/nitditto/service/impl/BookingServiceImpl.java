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
import com.nitditto.service.BookingCancellationService;
import com.nitditto.service.BookingConfirmationService;
import com.nitditto.service.BookingCreationService;
import com.nitditto.service.BookingEnrichmentService;
import com.nitditto.service.BookingPriceCalculator;
import com.nitditto.service.BookingRefundService;
import com.nitditto.service.BookingReadAccess;
import com.nitditto.service.BookingService;
import com.nitditto.service.PassengerService;
import com.nitditto.service.SeatReservationService;
import com.nitditto.service.TicketService;
import com.nitditto.service.integration.FareIntegrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final BookingCreationService bookingCreationService;
    private final BookingRefundService bookingRefundService;
    private final BookingCancellationService bookingCancellationService;

    /**
     * Điều phối đặt vé qua nhiều service — cố ý KHÔNG chạy trong transaction (CLAUDE.md §8): không giữ DB connection
     * trong lúc gọi HTTP, và payment chỉ được tạo SAU KHI booking đã commit.
     *
     * <ol>
     *   <li>Đọc dữ liệu từ service khác (chuyến bay, giá) — chưa ghi gì.</li>
     *   <li>Giữ ghế — ghế đã bị giữ/đặt thì dừng, chưa tạo gì.</li>
     *   <li>Ghi booking + hành khách + vé trong transaction riêng ({@link BookingCreationService}).
     *       Lỗi (kể cả lỗi lúc commit) → nhả ghế.</li>
     *   <li>Khởi tạo thanh toán. Lỗi → huỷ booking PENDING vừa tạo và nhả ghế.</li>
     * </ol>
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentInitiateResponse createBooking(final BookingRequest request, final UUID userId) {
        final List<UUID> seatInstanceIds = request.getPassengers().stream()
                .map(PassengerRequest::getSeatInstanceId)
                .collect(Collectors.toList());
        if (seatInstanceIds.stream().distinct().count() != seatInstanceIds.size()) {
            throw new BusinessException("Each passenger must be assigned a different seat");
        }

        final UUID airlineId = this.resolveAirlineId(request.getFlightId());
        final double totalPrice = this.calculateTotalPrice(request, seatInstanceIds);

        this.seatReservationService.hold(userId, seatInstanceIds);
        final Booking booking;
        try {
            booking = this.bookingCreationService.createPending(request, userId, airlineId, seatInstanceIds);
        } catch (final RuntimeException ex) {
            this.seatReservationService.releaseQuietly(userId, seatInstanceIds);
            throw ex;
        }

        try {
            return this.initiatePayment(request, userId, booking, totalPrice);
        } catch (final RuntimeException ex) {
            log.error("Payment initiation failed for booking {}; abandoning it", booking.getBookingReference(), ex);
            this.abandonQuietly(booking);
            this.seatReservationService.releaseQuietly(userId, seatInstanceIds);
            throw ex;
        }
    }

    private UUID resolveAirlineId(final UUID flightId) {
        final FlightResponse flight = this.flightClient.getFlightById(flightId).getData();
        if (flight == null || flight.getAirline() == null || flight.getAirline().getId() == null) {
            throw new ResourceNotFoundException("Flight", "id", flightId);
        }
        return flight.getAirline().getId();
    }

    private double calculateTotalPrice(final BookingRequest request, final List<UUID> seatInstanceIds) {
        final Double farePerPassenger = this.fareIntegrationService.calculateFareTotal(request.getFareId());
        final Double seatPrice = this.seatClient.calculateSeatPrice(seatInstanceIds);
        // ancillaryIds/mealIds là optional trong BookingRequest; Feign không cho body null (IllegalArgumentException -> 500).
        final Double ancillaryPrice = this.ancillaryClient.calculateAncillariesPrice(
                Objects.requireNonNullElse(request.getAncillaryIds(), List.of())).getData();
        final Double mealPrice = this.ancillaryClient.calculateMealPrice(
                Objects.requireNonNullElse(request.getMealIds(), List.of())).getData();
        return BookingPriceCalculator.total(farePerPassenger, request.getPassengers().size(), seatPrice,
                ancillaryPrice, mealPrice);
    }

    private PaymentInitiateResponse initiatePayment(final BookingRequest request, final UUID userId,
            final Booking booking, final double totalPrice) {
        // bắt buộc trong BookingRequest (@NotNull) nên không tự mặc định cổng thanh toán.
        final PaymentGateway gateway = request.getPaymentGateway();
        final PaymentInitiateRequest paymentInitiateRequest = PaymentInitiateRequest.builder()
                .userId(userId)
                .bookingId(booking.getId())
                .amount(totalPrice)
                .paymentGateway(gateway)
                .description("Payment for booking ID : " + booking.getBookingReference())
                .build();
        return this.paymentClient.initiatePayment(paymentInitiateRequest).getBody().getData();
    }

    // Bắt lỗi ở đây (ngoài proxy) chứ không trong method @Transactional: lỗi commit chỉ lộ ra sau khi method trả về.
    private void abandonQuietly(final Booking booking) {
        try {
            this.bookingCreationService.abandonPending(booking.getId());
        } catch (final RuntimeException ex) {
            log.warn("Could not abandon booking {} (it will expire after the payment window): {}",
                    booking.getBookingReference(), ex.getMessage());
        }
    }

    @Override
    public BookingResponse getBookingById(final UUID id, final UUID requesterUserId, final boolean requesterIsAdmin) {
        final Booking booking = this.findBooking(id);
        BookingReadAccess.require(booking, requesterUserId, requesterIsAdmin,
                () -> this.airlineIdOwnedBy(requesterUserId));
        return convertToBookingResponses(List.of(booking)).get(0);
    }

    private Booking findBooking(final UUID id) {
        return this.bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
    }

    // Hãng bay mà user là chủ sở hữu; null nếu user không phải chủ hãng nào (airline-core trả 404). Lỗi khác (service
    // down...) để nổi lên thành 5xx: không "đoán" quyền khi không xác minh được.
    private UUID airlineIdOwnedBy(final UUID userId) {
        try {
            final AirlineResponse airline = this.airlineClient.getAirlineByOwner(userId).getData();
            return airline != null ? airline.getId() : null;
        } catch (final FeignException.NotFound ex) {
            return null;
        }
    }

    @Override
    public BookingResponse confirmPayment(final UUID id) {
        // Chạy trong transaction riêng và commit trước khi đọc lại, nên bản đọc bên dưới thấy trạng thái mới.
        this.bookingConfirmationService.confirmPayment(id);
        // Lệnh nội bộ (payment-service gọi qua Eureka, gateway chỉ cho ADMIN): không kiểm tra chủ booking ở đây.
        return convertToBookingResponses(List.of(this.findBooking(id))).get(0);
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

    /**
     * Huỷ booking — cố ý KHÔNG chạy trong transaction (CLAUDE.md §8): có lời gọi HTTP sang pricing/flight/payment/seat.
     *
     * <ol>
     *   <li>Booking đã xác nhận (đã trả tiền): quyết định số hoàn theo chính sách và nhờ payment-service ghi nhận hoàn
     *       tiền TRƯỚC khi huỷ. Không ghi nhận được → lỗi, booking giữ nguyên, người dùng thử lại (yêu cầu hoàn là
     *       idempotent). Vé không đủ điều kiện hoàn thì vẫn huỷ, hoàn 0.</li>
     *   <li>Đổi trạng thái CANCELLED + huỷ vé trong transaction riêng ({@link BookingCancellationService}).</li>
     *   <li>Nhả ghế (HTTP, lỗi chỉ ghi log: ghế tự hết hạn giữ).</li>
     * </ol>
     * Huỷ lần hai trên booking đã huỷ không làm gì thêm (không hoàn lần hai).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookingResponse cancelBooking(final UUID id, final UUID requesterUserId) {
        final Booking booking = this.bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
        requireOwner(booking, requesterUserId, "cancel");
        final BookingStatus previous = booking.getBookingStatus();
        if (previous == BookingStatus.CANCELLED) {
            return convertToBookingResponses(List.of(booking)).get(0);
        }

        final BookingRefundService.Outcome refund = previous == BookingStatus.CONFIRMED
                ? this.bookingRefundService.refundForCancellation(booking, requesterUserId)
                : BookingRefundService.Outcome.notApplicable();

        final Booking updated = this.bookingCancellationService.markCancelled(id);
        this.releaseSeatsOf(booking, previous);

        final BookingResponse response = convertToBookingResponses(List.of(updated)).get(0);
        response.setRefundAmount(refund.amount());
        response.setRefundStatus(refund.status());
        response.setRefundNote(refund.note());
        return response;
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
