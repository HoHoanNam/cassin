package com.cassin.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Dữ liệu đăng nhập client gửi lên.
 * Chỉ kiểm tra "không rỗng": không cần @Email hay @Size ở đây,
 * vì email sai định dạng thì đằng nào cũng không tìm thấy user.
 */
public record LoginRequest(
    @NotBlank(message = "Vui lòng nhập email") String email,
    @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {}
