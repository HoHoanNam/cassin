package com.cassin.auth.dto;

import com.cassin.user.dto.UserResponse;

/**
 * Kết quả đăng nhập.
 *
 * @param accessToken JWT, client gửi lại trong header: Authorization: Bearer <accessToken>
 * @param tokenType   luôn là "Bearer" (chuẩn OAuth 2.0), cho client biết cách gửi token
 * @param expiresIn   số GIÂY token còn hiệu lực, để client biết khi nào cần đăng nhập lại
 * @param user        thông tin người dùng, để giao diện hiện tên ngay mà không cần gọi thêm /api/me
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
