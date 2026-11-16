package com.cassin.auth.dto;

/**
 * Kết quả đăng nhập / refresh ở tầng service: phần trả trong body + refresh token gốc.
 * Controller đặt refreshToken vào cookie HttpOnly, KHÔNG để nó lọt vào JSON
 * (JavaScript đọc được JSON, không đọc được cookie HttpOnly).
 */
public record AuthTokens(LoginResponse body, String refreshToken) {}
