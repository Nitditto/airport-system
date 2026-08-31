package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.SeatMapMapper;
import com.nitditto.model.CabinClass;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.SeatMapRequest;
import com.nitditto.payload.response.SeatMapResponse;
import com.nitditto.repository.CabinClassRepository;
import com.nitditto.repository.SeatMapRepository;
import com.nitditto.repository.SeatRepository;
import com.nitditto.service.SeatMapService;
import com.nitditto.service.SeatService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatMapServiceImpl implements SeatMapService {

    private final SeatMapRepository seatMapRepository;
    private final CabinClassRepository cabinClassRepository;
    private final SeatRepository seatRepository;
    private final SeatService seatService;

    @Override
    @Transactional
    public SeatMapResponse createSeatMap(final UUID airlineId, final SeatMapRequest seatMapRequest) {
        final CabinClass cabinClass = this.cabinClassRepository.findById(seatMapRequest.getCabinClassId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CabinClass", "id", seatMapRequest.getCabinClassId()));

        if (this.seatMapRepository.existsByCabinClassId(seatMapRequest.getCabinClassId())) {
            throw new BusinessException("Seat map already exists for this cabin class");
        }

        final SeatMap seatMap = SeatMapMapper.toEntity(seatMapRequest, cabinClass);
        seatMap.setAirlineId(airlineId);
        final SeatMap savedSeatMap = this.seatMapRepository.save(seatMap);

        // generate seats for seat map
        seatService.generateSeats(savedSeatMap.getId());
        return SeatMapMapper.toResponse(savedSeatMap, cabinClass);
    }

    @Override
    public SeatMapResponse getSeatMapById(final UUID seatMapId) {
        final SeatMap seatMap = this.seatMapRepository.findById(seatMapId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatMap", "id", seatMapId));
        final CabinClass cabinClass = this.cabinClassRepository.findById(seatMap.getCabinClassId())
                .orElseThrow(() -> new ResourceNotFoundException("CabinClass", "id", seatMap.getCabinClassId()));
        return SeatMapMapper.toResponse(seatMap, cabinClass);
    }

    @Override
    public SeatMapResponse getSeatMapByCabinClassId(final UUID cabinClassId) {
        final SeatMap seatMap = this.seatMapRepository.findByCabinClassId(cabinClassId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatMap", "cabinClassId", cabinClassId));
        final CabinClass cabinClass = this.cabinClassRepository.findById(seatMap.getCabinClassId())
                .orElseThrow(() -> new ResourceNotFoundException("CabinClass", "id", seatMap.getCabinClassId()));
        return SeatMapMapper.toResponse(seatMap, cabinClass);
    }

    @Override
    @Transactional
    public SeatMapResponse updateSeatMap(final UUID seatMapId, final SeatMapRequest seatMapRequest) {
        final SeatMap seatMap = this.seatMapRepository.findById(seatMapId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatMap", "id", seatMapId));
        final CabinClass cabinClass = this.cabinClassRepository.findById(seatMapRequest.getCabinClassId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CabinClass", "id", seatMapRequest.getCabinClassId()));

        if (this.seatMapRepository.existsByCabinClassIdAndIdNot(
                seatMapRequest.getCabinClassId(), seatMap.getId())) {
            throw new BusinessException("Seat map already exists for this cabin class");
        }

        // Ghế đã sinh sẵn theo đúng layout/cabinClassId cũ (xem SeatServiceImpl.generateSeats).
        // Đổi 1 trong các field này mà không sinh lại ghế sẽ làm Seat lệch khỏi SeatMap:
        // thừa/thiếu ghế nếu đổi kích thước, hoặc Seat.cabinClassId trỏ sai nếu đổi cabin class.
        final boolean layoutChanged = seatMap.getTotalRows() != seatMapRequest.getTotalRows()
                || seatMap.getLeftSeatsPerRow() != seatMapRequest.getLeftSeatsPerRow()
                || seatMap.getRightSeatsPerRow() != seatMapRequest.getRightSeatsPerRow();
        final boolean cabinClassChanged = !seatMap.getCabinClassId().equals(cabinClass.getId());

        SeatMapMapper.updateEntity(seatMapRequest, seatMap);
        seatMap.setCabinClassId(cabinClass.getId());
        final SeatMap updatedSeatMap = this.seatMapRepository.save(seatMap);

        if (layoutChanged || cabinClassChanged) {
            // Không thể giữ lại tuỳ biến của từng ghế cũ (giá, trạng thái...) vì layout đã đổi
            // nên vị trí/ý nghĩa của từng ghế không còn tương ứng 1-1 với trước.
            this.seatRepository.deleteBySeatMapId(updatedSeatMap.getId());
            // Bắt buộc flush trước khi sinh lại: trong cùng 1 flush Hibernate chạy INSERT trước
            // DELETE, ghế mới sẽ đâm vào unique constraint (seat_map_id, seat_number) của ghế cũ.
            this.seatRepository.flush();
            this.seatService.generateSeats(updatedSeatMap.getId());
        }

        return SeatMapMapper.toResponse(updatedSeatMap, cabinClass);
    }

    @Override
    @Transactional
    public void deleteSeatMap(final UUID seatMapId) {
        if (!this.seatMapRepository.existsById(seatMapId)) {
            throw new ResourceNotFoundException("SeatMap", "id", seatMapId);
        }
        this.seatRepository.deleteBySeatMapId(seatMapId);
        this.seatMapRepository.deleteById(seatMapId);
    }
}
