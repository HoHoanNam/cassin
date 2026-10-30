package com.cassin.user.dto;

import com.cassin.user.Role;
import com.cassin.user.User;

// Dữ liệu trả về. Tách riêng khỏi entity để KHÔNG BAO GIỜ lộ passwordHash ra ngoài.
public record UserResponse(Long id, String email, String displayName, Role role, String bio) {
  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getBio());
  }
}
