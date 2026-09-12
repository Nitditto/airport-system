package com.nitditto.service.impl;

import com.nitditto.enums.BookingStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.BookingEnrichmentContext;
import com.nitditto.mapper.BookingMapper;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.payload.response.BookingResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.payload.response.TicketResponse;
import com.nitditto.repository.BookingRepository;
import com.nitditto.repository.spec.BookingSpecs;
import com.nitditto.service.BookingService;
import com.nitditto.service.PassengerService;
import com.nitditto.service.TicketService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final PassengerService passengerService;
    private final TicketService ticketService;

    @Override
    @Transactional
    public BookingResponse createBooking(final BookingRequest request, final UUID userId) {
        final String bookingReference = generateBookingReference();

        final List<UUID> seatInstanceIds = request.getPassengers().stream()
                .map(PassengerRequest::getSeatInstanceId)
                .collect(Collectors.toList());

        // Lưu Booking trước để lấy id (UUIDv7 do Hibernate sinh ngay khi save, không cần flush),
        // rồi mới tạo Passenger/Ticket giữ bookingId — cả 3 nằm trong cùng 1 transaction vì
        // passengerService/ticketService là bean khác, lời gọi @Transactional của chúng join
        // vào transaction đã mở ở đây (propagation REQUIRED mặc định).
        Booking booking = BookingMapper.toEntity(request, userId, bookingReference, seatInstanceIds);
        booking = this.bookingRepository.saveAndFlush(booking);

        final List<PassengerResponse> passengers = this.passengerService
                .createPassengers(request.getPassengers(), booking.getId(), userId);
        final List<TicketResponse> tickets = enrichTickets(
                this.ticketService.generateTicketsForBooking(booking.getId(), passengers), booking, passengers);

        // todo step 7: calculate price from fare + seat + ancillary + meal (out of scope)
        // todo step 8: initiate payment via payment-service (out of scope)

        return BookingMapper.toResponse(booking, buildContext(passengers, tickets));
    }

    @Override
    public BookingResponse getBookingById(final UUID id) {
        final Booking booking = this.bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
        return convertToBookingResponses(List.of(booking)).get(0);
    }

    @Override
    public Page<BookingResponse> getAllBookingsByAirline(final UUID airlineId, final String search,
            final BookingStatus status, final UUID flightInstanceId, final Pageable pageable) {
        final Specification<Booking> spec = Specification
                .where(BookingSpecs.airlineId(airlineId))
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

        booking.setBookingStatus(BookingStatus.CANCELLED);
        final Booking updated = this.bookingRepository.save(booking);
        this.ticketService.cancelTicketsForBooking(id);

        // todo: khi có payment-service, gọi refund/void payment ở đây

        return convertToBookingResponses(List.of(updated)).get(0);
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

    // Công thức batch-load chuẩn: gom distinct bookingId -> 1 query IN cho mỗi loại con
    // (passenger, ticket) -> Map -> ghép trong bộ nhớ. Số query hằng số theo số booking.
    private List<BookingResponse> convertToBookingResponses(final List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        final List<UUID> bookingIds = bookings.stream().map(Booking::getId).distinct().toList();

        final Map<UUID, List<PassengerResponse>> passengersByBookingId = this.passengerService
                .findByBookingIds(bookingIds);
        final Map<UUID, List<TicketResponse>> ticketsByBookingId = this.ticketService.findByBookingIds(bookingIds);

        final List<BookingResponse> responses = new ArrayList<>(bookings.size());
        for (final Booking booking : bookings) {
            final List<PassengerResponse> passengers = passengersByBookingId.getOrDefault(booking.getId(), List.of());
            final List<TicketResponse> tickets = enrichTickets(
                    ticketsByBookingId.getOrDefault(booking.getId(), List.of()), booking, passengers);
            responses.add(BookingMapper.toResponse(booking, buildContext(passengers, tickets)));
        }
        return responses;
    }

    // TicketMapper không được phép tự đi lấy Booking/Passenger (rule NO-JPA-relationship
    // kéo theo mapper thuần không traversal) -> điền field phẳng denormalized ở đây, nơi
    // đã có sẵn cả Booking lẫn danh sách Passenger tương ứng, không cần query thêm.
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

    // todo: khi có Feign client tới flight-ops/pricing/seat/ancillary-service, batch-fetch
    // theo đúng công thức N+1 (distinct id -> 1 query IN mỗi loại -> Map) thay vì stub rỗng
    // như hiện tại — xem "Loại 2" trong CLAUDE.md.
    private BookingEnrichmentContext buildContext(final List<PassengerResponse> passengers,
            final List<TicketResponse> tickets) {
        return new BookingEnrichmentContext(
                new PaymentDTO(), new FareResponse(), new FlightResponse(), new FlightInstanceResponse(),
                List.of(), List.of(), List.of(),
                passengers, tickets);
    }
}
