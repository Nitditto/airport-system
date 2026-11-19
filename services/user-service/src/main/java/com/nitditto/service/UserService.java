package com.nitditto.service;

import com.nitditto.payload.dto.UserDTO;
import java.util.List;
import java.util.UUID;

public interface UserService {

  UserDTO getUserByEmail(String email);

  /**
   * Trả thông tin user theo id. Chỉ chính chủ (email trong JWT trùng email của user) hoặc SYSTEM_ADMIN được đọc;
   * người khác nhận AccessDeniedException (403) — chặn IDOR đọc thông tin user khác qua id.
   */
  UserDTO getUserById(UUID id, String requesterEmail, boolean requesterIsAdmin);

  List<UserDTO> getAllUsers();
}
