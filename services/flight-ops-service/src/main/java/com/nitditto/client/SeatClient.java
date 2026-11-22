package com.nitditto.client;

import com.nitditto.enums.CabinClassType;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.CabinClassResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "seat-service")
public interface SeatClient {

  /** Hạng ghế còn bán của từng máy bay theo loại; key là aircraftId, máy bay không có hạng đó thì vắng. */
  @PostMapping("/api/cabin-classes/batch-by-aircraft-ids")
  ApiResponse<Map<UUID, CabinClassResponse>> getBookableCabinClasses(
      @RequestParam("cabinClass") CabinClassType cabinClass, @RequestBody List<UUID> aircraftIds);
}
