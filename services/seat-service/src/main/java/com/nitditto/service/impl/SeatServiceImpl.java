package com.nitditto.service.impl;

import com.nitditto.enums.SeatType;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.SeatMapper;
import com.nitditto.model.CabinClass;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.SeatRequest;
import com.nitditto.payload.response.SeatResponse;
import com.nitditto.repository.CabinClassRepository;
import com.nitditto.repository.SeatMapRepository;
import com.nitditto.repository.SeatRepository;
import com.nitditto.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final SeatMapRepository seatMapRepository;
    private final CabinClassRepository cabinClassRepository;

    @Override
    @Transactional
    public void generateSeats(final UUID seatMapId) {
        if (this.seatRepository.existsBySeatMapId(seatMapId)) {
            throw new BusinessException("Seats already generated for this seat map");
        }
        final SeatMap seatMap = this.seatMapRepository.findById(seatMapId).orElseThrow(
                () -> new ResourceNotFoundException("SeatMap", "id", seatMapId));

        final int leftSeatsPerRow = seatMap.getLeftSeatsPerRow();
        final int rightSeatsPerRow = seatMap.getRightSeatsPerRow();
        final int rows = seatMap.getTotalRows();
        final int seatsPerRow = leftSeatsPerRow + rightSeatsPerRow;

        final List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int col = 0; col < seatsPerRow; col++) {
                final String seatLetter = getSeatLetter(col);
                final SeatType type = getSeatType(col, leftSeatsPerRow, rightSeatsPerRow);
                final Seat seat = Seat.builder()
                        .seatNumber(row + seatLetter)
                        .seatRow(row)
                        .columnLetter(seatLetter.charAt(0))
                        .seatType(type)
                        .seatMapId(seatMapId)
                        .cabinClassId(seatMap.getCabinClassId())
                        .build();

                seats.add(seat);
            }
        }
        this.seatRepository.saveAll(seats);
    }

    private static SeatType getSeatType(final int col, final int leftSeatsPerRow, final int rightSeatsPerRow) {
        final int totalSeats = leftSeatsPerRow + rightSeatsPerRow;
        if (col == 0 || col == totalSeats - 1) {
            return SeatType.WINDOW;
        }
        if (col == leftSeatsPerRow - 1 || col == leftSeatsPerRow) {
            return SeatType.AISLE;
        }
        return SeatType.MIDDLE;
    }

    // 0=>A, 1=>B, ..., 25=>Z, 26=>AA, 27=>AB, ...
    private static String getSeatLetter(int col) {
        final StringBuilder sb = new StringBuilder();
        while (col >= 0) {
            sb.insert(0, (char) ('A' + col % 26));
            col = col / 26 - 1;
        }
        return sb.toString();
    }

    @Override
    public Page<SeatResponse> getSeatsBySeatMapId(final UUID seatMapId, final Pageable pageable) {
        final SeatMap seatMap = this.seatMapRepository.findById(seatMapId).orElseThrow(
                () -> new ResourceNotFoundException("SeatMap", "id", seatMapId));
        final CabinClass cabinClass = this.cabinClassRepository.findById(seatMap.getCabinClassId()).orElseThrow(
                () -> new ResourceNotFoundException("CabinClass", "id", seatMap.getCabinClassId()));

        // Toàn bộ trang seats đều thuộc đúng 1 seatMapId -> chỉ cần 1 SeatMap + 1 CabinClass
        // load sẵn ở trên, KHÔNG query lại trong loop. Tổng cộng luôn là 3 query, không phụ
        // thuộc page size (không cần Map<UUID,...> batch vì không có nhiều "cha" khác nhau).
        final Page<Seat> page = this.seatRepository.findBySeatMapId(seatMapId, pageable);
        return page.map(seat -> SeatMapper.toResponse(seat, seatMap, cabinClass));
    }

    @Override
    public SeatResponse getSeatById(final UUID seatId) {
        final Seat seat = this.seatRepository.findById(seatId).orElseThrow(
                () -> new ResourceNotFoundException("Seat", "id", seatId));
        final SeatMap seatMap = this.seatMapRepository.findById(seat.getSeatMapId()).orElseThrow(
                () -> new ResourceNotFoundException("SeatMap", "id", seat.getSeatMapId()));
        final CabinClass cabinClass = this.cabinClassRepository.findById(seat.getCabinClassId()).orElseThrow(
                () -> new ResourceNotFoundException("CabinClass", "id", seat.getCabinClassId()));
        return SeatMapper.toResponse(seat, seatMap, cabinClass);
    }

    @Override
    @Transactional
    public SeatResponse updateSeat(final UUID seatId, final SeatRequest seatRequest) {
        final Seat seat = this.seatRepository.findById(seatId).orElseThrow(
                () -> new ResourceNotFoundException("Seat", "id", seatId));
        SeatMapper.updateEntity(seatRequest, seat);
        final Seat updatedSeat = this.seatRepository.save(seat);

        final SeatMap seatMap = this.seatMapRepository.findById(updatedSeat.getSeatMapId()).orElseThrow(
                () -> new ResourceNotFoundException("SeatMap", "id", updatedSeat.getSeatMapId()));
        final CabinClass cabinClass = this.cabinClassRepository.findById(updatedSeat.getCabinClassId()).orElseThrow(
                () -> new ResourceNotFoundException("CabinClass", "id", updatedSeat.getCabinClassId()));
        return SeatMapper.toResponse(updatedSeat, seatMap, cabinClass);
    }
}
