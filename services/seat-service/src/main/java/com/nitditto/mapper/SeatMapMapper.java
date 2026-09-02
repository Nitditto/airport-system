package com.nitditto.mapper;

import java.util.List;
import java.util.stream.Collectors;

import com.nitditto.model.CabinClass;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.SeatMapRequest;
import com.nitditto.payload.response.SeatMapResponse;

public class SeatMapMapper {

    public static SeatMap toEntity(SeatMapRequest seatMapRequest, CabinClass cabinClass) {
        return SeatMap.builder()
                .name(seatMapRequest.getName())
                .totalRows(seatMapRequest.getTotalRows())
                .leftSeatsPerRow(seatMapRequest.getLeftSeatsPerRow())
                .rightSeatsPerRow(seatMapRequest.getRightSeatsPerRow())
                .cabinClassId(cabinClass != null ? cabinClass.getId() : null)
                .build();
    }

    public static void updateEntity(SeatMapRequest seatMapRequest, SeatMap seatMap) {
        seatMap.setName(seatMapRequest.getName());
        seatMap.setTotalRows(seatMapRequest.getTotalRows());
        seatMap.setLeftSeatsPerRow(seatMapRequest.getLeftSeatsPerRow());
        seatMap.setRightSeatsPerRow(seatMapRequest.getRightSeatsPerRow());
    }

    /**
     * {@code cabinClass}/{@code seats} do service batch-load sẵn rồi truyền vào (không
     * traverse quan hệ, vì SeatMap chỉ giữ UUID thô theo rule "NO JPA Relationship" của
     * project — không có {@code SeatMap.getSeats()}). Truyền {@code null} cho tham số nào
     * caller không cần/không load — {@code cabinClassName}/{@code cabinClassCode} sẽ null
     * nếu thiếu {@code cabinClass}; {@code seats}/{@code availableSeats}/{@code windowSeats}...
     * sẽ null/0 nếu thiếu {@code seats}. {@code totalSeats} suy ra ngay từ layout nên luôn có.
     */
    public static SeatMapResponse toResponse(final SeatMap seatMap, final CabinClass cabinClass,
            final List<Seat> seats) {
        if (seatMap == null) return null;

        final int totalSeats = seats != null ? seats.size() : 0;
        final int availableSeats = seats != null ? (int) seats.stream()
                .filter(seat -> Boolean.TRUE.equals(seat.getIsAvailable()) && Boolean.TRUE.equals(seat.getIsActive())
                        && !Boolean.TRUE.equals(seat.getIsBooked()))
                .count() : 0;

        final int windowSeats = seats != null
                ? (int) seats.stream().filter(seat -> seat.getSeatType() != null
                        && seat.getSeatType().name().contains("WINDOW")).count()
                : 0;
        final int middleSeats = seats != null
                ? (int) seats.stream().filter(seat -> seat.getSeatType() != null
                        && seat.getSeatType().name().contains("MIDDLE")).count()
                : 0;
        final int aisleSeats = seats != null
                ? (int) seats.stream().filter(seat -> seat.getSeatType() != null
                        && seat.getSeatType().name().contains("AISLE")).count()
                : 0;
        final int emergencyExitSeats = seats != null
                ? (int) seats.stream().filter(seat -> Boolean.TRUE.equals(seat.getIsEmergencyExit())).count()
                : 0;
        final int premiumSeats = seats != null ? (int) seats.stream()
                .filter(seat -> Boolean.TRUE.equals(seat.getHasExtraLegroom())
                        || Boolean.TRUE.equals(seat.getIsEmergencyExit())
                        || Boolean.TRUE.equals(seat.getHasExtraWidth())
                        || Boolean.TRUE.equals(seat.getHasTvScreen()))
                .count() : 0;

        return SeatMapResponse.builder()
                .id(seatMap.getId())
                .name(seatMap.getName())
                .totalRows(seatMap.getTotalRows())
                .leftSeatsPerRow(seatMap.getLeftSeatsPerRow())
                .rightSeatsPerRow(seatMap.getRightSeatsPerRow())
                .airlineId(seatMap.getAirlineId())
                .cabinClassId(seatMap.getCabinClassId())
                .cabinClassName(cabinClass != null ? cabinClass.getName().toString() : null)
                .cabinClassCode(cabinClass != null ? cabinClass.getCode() : null)
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .occupiedSeats(totalSeats - availableSeats)
                .seats(seats != null
                        ? seats.stream().map(seat -> SeatMapper.toResponse(seat, seatMap, cabinClass))
                                .collect(Collectors.toList())
                        : null)
                .windowSeats(windowSeats)
                .middleSeats(middleSeats)
                .aisleSeats(aisleSeats)
                .emergencyExitSeats(emergencyExitSeats)
                .premiumSeats(premiumSeats)
                .build();
    }

    /** Chỉ layout, không kèm list ghế -> dùng khi lồng SeatMap vào response khác (vd. FlightInstanceCabin). */
    public static SeatMapResponse toSimpleResponse(final SeatMap seatMap) {
        if (seatMap == null) return null;

        return SeatMapResponse.builder()
                .id(seatMap.getId())
                .name(seatMap.getName())
                .totalRows(seatMap.getTotalRows())
                .leftSeatsPerRow(seatMap.getLeftSeatsPerRow())
                .rightSeatsPerRow(seatMap.getRightSeatsPerRow())
                .cabinClassId(seatMap.getCabinClassId())
                .build();
    }
}
