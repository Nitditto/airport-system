package com.nitditto.seat_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nitditto.controller.SeatInstanceController;
import com.nitditto.exception.GlobalExceptionHandler;
import com.nitditto.service.SeatHoldService;
import com.nitditto.service.SeatInstanceService;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** batch-by-ids vượt 200 id phải trả 400 (HandlerMethodValidationException), không rơi xuống catch-all 500. */
@ExtendWith(MockitoExtension.class)
class SeatInstanceBatchLimitTest {

  @Mock
  private SeatInstanceService seatInstanceService;
  @Mock
  private SeatHoldService seatHoldService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders
        .standaloneSetup(new SeatInstanceController(this.seatInstanceService, this.seatHoldService))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test
  void moreThan200Ids_returns400_andNeverHitsTheService() throws Exception {
    this.mockMvc.perform(post("/api/seat-instances/batch-by-ids").contentType(MediaType.APPLICATION_JSON)
            .content(ids(201)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("At most 200 ids per batch request"));

    verify(this.seatInstanceService, never()).getSeatInstancesByIds(any());
  }

  @Test
  void exactly200Ids_isAccepted() throws Exception {
    when(this.seatInstanceService.getSeatInstancesByIds(any())).thenReturn(Map.of());

    this.mockMvc.perform(post("/api/seat-instances/batch-by-ids").contentType(MediaType.APPLICATION_JSON)
            .content(ids(200)))
        .andExpect(status().isOk());
  }

  private static String ids(final int count) {
    return IntStream.range(0, count).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
        .collect(Collectors.joining(",", "[", "]"));
  }
}
