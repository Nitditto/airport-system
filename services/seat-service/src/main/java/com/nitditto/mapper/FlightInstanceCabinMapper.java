package com.nitditto.mapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.nitditto.model.CabinClass;
import com.nitditto.model.FlightInstanceCabin;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatInstance;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.FlightInstanceCabinRequest;
import com.nitditto.payload.response.FlightInstanceCabinResponse;

public class FlightInstanceCabinMapper {

    /**
     * {@code cabinClass}/{@code seatMap} do service batch-load sẵn rồi truyền vào (không
     * traverse quan hệ, vì FlightInstanceCabin chỉ giữ cabinClassId dạng UUID thô).
     * {@code seatInstances}/{@code seatById} để {@code null} ở list view (chỉ trả số liệu
     * tổng hợp sẵn có trên chính entity, không đụng bảng seat_instances) — chỉ populate ở
     * detail view (xem 1 flight instance cabin).
     */
    public record FlightInstanceCabinContext(
            FlightInstanceCabin flightInstanceCabin,
            CabinClass cabinClass,
            SeatMap seatMap,
            List<SeatInstance> seatInstances,
            Map<UUID, Seat> seatById) {
    }

    public static FlightInstanceCabinResponse toResponse(final FlightInstanceCabinContext context) {
        if (context == null || context.flightInstanceCabin() == null) return null;

        final FlightInstanceCabin cabin = context.flightInstanceCabin();
        final CabinClass cabinClass = context.cabinClass();
        final List<SeatInstance> seatInstances = context.seatInstances();
        final Map<UUID, Seat> seatById = context.seatById();

        return FlightInstanceCabinResponse.builder()
                .id(cabin.getId())
                .flightInstanceId(cabin.getFlightInstanceId())
                .cabinClassType(cabinClass != null ? cabinClass.getName() : null)
                // seatMap = null tránh lồng CabinClassResponse.seatMap 3 tầng vào chính response này
                .cabinClass(CabinClassMapper.toResponse(cabinClass, null))
                .seatMap(SeatMapMapper.toSimpleResponse(context.seatMap()))
                .seats(seatInstances != null
                        ? seatInstances.stream()
                                .map(si -> SeatInstanceMapper.toResponse(new SeatInstanceMapper.SeatInstanceContext(
                                        si,
                                        seatById != null ? seatById.get(si.getSeatId()) : null,
                                        context.seatMap(),
                                        cabinClass)))
                                .collect(Collectors.toList())
                        : null)
                .baseFare(cabin.getBaseFare())
                .windowSurcharge(cabin.getWindowSurcharge())
                .aisleSurcharge(cabin.getAisleSurcharge())
                .taxesAndFees(cabin.getTaxesAndFees())
                .airlineFees(cabin.getAirlineFees())
                .currentPrice(cabin.getCurrentPrice())
                .totalCabinPrice(cabin.getTotalCabinPrice())
                .totalSeats(cabin.getTotalSeats())
                .bookedSeats(cabin.getBookedSeats())
                .availableSeats(cabin.getAvailableSeats())
                .isActive(cabin.getIsActive())
                .canBook(Boolean.TRUE.equals(cabin.getIsActive())
                        && cabin.getAvailableSeats() != null && cabin.getAvailableSeats() > 0)
                .build();
    }

    public static void updateEntity(final FlightInstanceCabinRequest request, final FlightInstanceCabin cabin) {
        if (request == null || cabin == null) return;
        if (request.getBaseFare() != null) cabin.setBaseFare(request.getBaseFare());
        if (request.getWindowSurcharge() != null) cabin.setWindowSurcharge(request.getWindowSurcharge());
        if (request.getAisleSurcharge() != null) cabin.setAisleSurcharge(request.getAisleSurcharge());
        if (request.getTaxesAndFees() != null) cabin.setTaxesAndFees(request.getTaxesAndFees());
        if (request.getAirlineFees() != null) cabin.setAirlineFees(request.getAirlineFees());
        if (request.getCurrentPrice() != null) cabin.setCurrentPrice(request.getCurrentPrice());
        if (request.getIsActive() != null) cabin.setIsActive(request.getIsActive());
    }
}
