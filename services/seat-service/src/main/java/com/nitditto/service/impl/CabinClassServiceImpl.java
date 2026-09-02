package com.nitditto.service.impl;

import com.nitditto.enums.CabinClassType;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.CabinClassMapper;
import com.nitditto.model.CabinClass;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.CabinClassRequest;
import com.nitditto.payload.response.CabinClassResponse;
import com.nitditto.repository.CabinClassRepository;
import com.nitditto.repository.SeatMapRepository;
import com.nitditto.repository.SeatRepository;
import com.nitditto.service.CabinClassService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CabinClassServiceImpl implements CabinClassService {

    private final CabinClassRepository cabinClassRepository;
    private final SeatMapRepository seatMapRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public CabinClassResponse createCabinClass(CabinClassRequest cabinClassRequest) {
        // code luôn được CabinClassMapper lưu dạng viết hoa -> phải so trùng cùng dạng
        if (cabinClassRepository.existsByCodeAndAircraftId(cabinClassRequest.getCode().toUpperCase(),
                cabinClassRequest.getAircraftId())) {
            throw new BusinessException("Cabin class with code already exist");
        }

        // Một tàu bay chỉ có tối đa 1 hạng ghế mỗi loại
        if (cabinClassRepository.existsByAircraftIdAndName(cabinClassRequest.getAircraftId(),
                CabinClassType.valueOf(cabinClassRequest.getName()))) {
            throw new BusinessException("Cabin class of this type already exists for this aircraft");
        }
        CabinClass cabinClass = CabinClassMapper.toEntity(cabinClassRequest);
        CabinClass savedCabin = cabinClassRepository.save(cabinClass);
        return CabinClassMapper.toResponse(savedCabin, null);
    }

    @Override
    public CabinClassResponse getCabinClassById(UUID id) {
        CabinClass cabinClass = cabinClassRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("CabinClass", "id", id));
        SeatMap seatMap = seatMapRepository.findByCabinClassId(cabinClass.getId()).orElse(null);
        return CabinClassMapper.toResponse(cabinClass, seatMap);
    }

    @Override
    public List<CabinClassResponse> getCabinClassesByAircraftId(UUID aircraftId) {
        List<CabinClass> cabinClasses = cabinClassRepository.findByAircraftId(aircraftId);
        if (cabinClasses.isEmpty()) {
            return List.of();
        }

        // Batch: 1 query IN thay vì gọi repository trong .map() (N+1)
        List<UUID> cabinClassIds = cabinClasses.stream().map(CabinClass::getId).distinct().toList();
        Map<UUID, SeatMap> seatMapByCabinClassId = seatMapRepository.findByCabinClassIdIn(cabinClassIds)
                .stream().collect(Collectors.toMap(SeatMap::getCabinClassId, Function.identity()));

        return cabinClasses.stream()
                .map(cc -> CabinClassMapper.toResponse(cc, seatMapByCabinClassId.get(cc.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public CabinClassResponse getByAircraftIdAndName(UUID aircraftId, CabinClassType name) {
        CabinClass cabinClass = cabinClassRepository.findByAircraftIdAndName(aircraftId, name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CabinClass", "aircraftId+name", aircraftId + "/" + name));
        SeatMap seatMap = seatMapRepository.findByCabinClassId(cabinClass.getId()).orElse(null);
        return CabinClassMapper.toResponse(cabinClass, seatMap);
    }

    @Override
    @Transactional
    public CabinClassResponse updateCabinClass(UUID id, CabinClassRequest cabinClassRequest) {
        CabinClass cabinClass = cabinClassRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("CabinClass", "id", id));

        // Dùng aircraftId CỦA ENTITY, không phải của request: updateEntity không đổi aircraftId,
        // nên check theo request sẽ soi nhầm sang máy bay khác và bỏ lọt code trùng thật.
        if (cabinClassRepository.existsByCodeAndAircraftIdAndIdNot(cabinClassRequest.getCode().toUpperCase(),
                cabinClass.getAircraftId(), cabinClass.getId())) {
            throw new BusinessException("Cabin class with code already exist");
        }

        // name có thể đổi qua updateEntity -> phải soát lại ràng buộc 1 loại/tàu bay.
        // Lấy giá trị mới từ request, không có thì giữ nguyên giá trị hiện tại.
        final CabinClassType name = cabinClassRequest.getName() != null
                ? CabinClassType.valueOf(cabinClassRequest.getName())
                : cabinClass.getName();
        if (cabinClassRepository.existsByAircraftIdAndNameAndIdNot(
                cabinClass.getAircraftId(), name, cabinClass.getId())) {
            throw new BusinessException("Cabin class of this type already exists for this aircraft");
        }
        CabinClassMapper.updateEntity(cabinClassRequest, cabinClass);
        CabinClass savedCabin = cabinClassRepository.save(cabinClass);
        SeatMap seatMap = seatMapRepository.findByCabinClassId(savedCabin.getId()).orElse(null);
        return CabinClassMapper.toResponse(savedCabin, seatMap);
    }

    @Override
    @Transactional
    public void deleteCabinClass(UUID id) {
        if (!cabinClassRepository.existsById(id)) {
            throw new ResourceNotFoundException("CabinClass", "id", id);
        }
        // Tự xoá các thực thể con trước (Seat, SeatMap), sau đó xoá CabinClass trong cùng 1 transaction
        seatRepository.deleteByCabinClassId(id);
        seatMapRepository.deleteByCabinClassId(id);
        cabinClassRepository.deleteById(id);
    }
}
