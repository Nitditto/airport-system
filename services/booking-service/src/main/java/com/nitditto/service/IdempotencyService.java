package com.nitditto.service;

import java.util.UUID;
import java.util.function.Supplier;

public interface IdempotencyService {

  /**
   * Chạy {@code action} đúng một lần cho mỗi (user, key). Gọi lại cùng key + cùng {@code request} trong 24h trả về kết
   * quả đã lưu mà không chạy lại. Key null/rỗng thì chạy bình thường (không idempotent).
   *
   * <p>Không được gọi bên trong một transaction đang mở: mỗi bước ghi bản ghi phải commit riêng để request song song
   * nhìn thấy.
   *
   * @throws com.nitditto.exception.IdempotencyConflictException 409 khi request trước còn đang xử lý, 422 khi cùng key
   *     nhưng body khác
   */
  <T> T execute(UUID userId, String idempotencyKey, Object request, Class<T> responseType, Supplier<T> action);
}
