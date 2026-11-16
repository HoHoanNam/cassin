package com.cassin.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.cassin.auth.dto.RefreshTokenProperties;

import lombok.RequiredArgsConstructor;

/**
 * Cookie chứa refresh token. Trả về giá trị cho header "Set-Cookie".
 *
 *   HttpOnly        JavaScript KHÔNG đọc được → script lạ (XSS) không lấy được token.
 *   SameSite=Strict chỉ gửi kèm request xuất phát từ CHÍNH trang Cassin → chặn CSRF.
 *   Path=/api/auth  chỉ gửi kèm refresh / logout, không đi theo mọi request.
 *   Secure          chỉ gửi qua HTTPS (bật khi deploy).
 */
@Component
@RequiredArgsConstructor
public class RefreshCookie {
  public static final String NAME = "cassin_refresh";
  static final String PATH = "/api/auth";

  private final RefreshTokenProperties properties;

  // [THÊM]
  public String set(String rawToken) {
    return base(rawToken).maxAge(properties.ttl()).build().toString();
  }

  // [THÊM] Xóa cookie: cùng tên, CÙNG PATH (khác path thì trình duyệt coi là cookie khác), Max-Age=0.
  public String clear() {
    return base("").maxAge(0).build().toString();
  }

  // [THÊM]
  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Strict")
        .path(PATH);
  }
}
