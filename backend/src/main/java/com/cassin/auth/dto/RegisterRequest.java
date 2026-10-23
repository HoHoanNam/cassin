package com.cassin.auth.dto;

import jakarta.validation.constraints.*;

// Dùng để validate dữ liệu đầu vào của user khi đăng ký tài khoản.
// Record: class chỉ chứa dữ liệu, tự có constructor, getter, equals, hashCode, toString...
public record RegisterRequest(
  @NotBlank(message = "Vui lòng nhập email")
  @Email(message = "Email không hợp lệ")
  @Size(max = 255)
  String email,

  @NotBlank(message = "Vui lòng nhập mật khẩu")
  // BCrypt chỉ dùng 72 byte đầu, nên chặn luôn mật khẩu quá dài.
  @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
  String password,

  @NotBlank(message = "Vui lòng nhập tên hiển thị")
  @Size(max = 100)
  String displayName
) {}

