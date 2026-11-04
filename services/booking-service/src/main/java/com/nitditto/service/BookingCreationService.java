package com.nitditto.service;

import com.nitditto.model.Booking;
import com.nitditto.payload.request.BookingRequest;
import java.util.List;
import java.util.UUID;

/**
 * Phần GHI DB của luồng đặt vé, tách khỏi {@link BookingService} để transaction chỉ bao quanh việc ghi
 * (không bao quanh lời gọi HTTP) và để người gọi biết chắc dữ liệu đã COMMIT khi method trả về.
 * Mỗi method là một transaction riêng — phải gọi qua bean này (qua proxy), không gọi nội bộ.
 */
public interface BookingCreationService {

  /** Tạo booking PENDING kèm hành khách và vé trong một transaction; trả về booking đã commit. */
  Booking createPending(BookingRequest request, UUID userId, UUID airlineId, List<UUID> seatInstanceIds);

  /**
   * Huỷ booking PENDING vừa tạo khi không khởi tạo được thanh toán (người dùng chưa nhận được link thanh toán
   * nên không thể đã trả tiền). Booking đã rời PENDING thì giữ nguyên.
   * Có thể ném lỗi — người gọi tự quyết định nuốt lỗi (bắt NGOÀI proxy, vì lỗi commit chỉ lộ ra sau khi
   * method trả về); nếu bỏ qua, booking vẫn tự chuyển EXPIRED khi quá hạn thanh toán.
   */
  void abandonPending(UUID bookingId);
}
