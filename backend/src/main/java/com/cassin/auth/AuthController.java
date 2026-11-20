package com.cassin.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.dto.AuthTokens;
import com.cassin.auth.dto.LoginRequest;
import com.cassin.auth.dto.LoginResponse;
import com.cassin.auth.dto.RegisterRequest;
import com.cassin.user.dto.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Các API công khai: ai cũng gọi được, không cần access token
 * (SecurityConfig mở toàn bộ /api/auth/**).
 *
 * refresh và logout dựa vào COOKIE refresh token (HttpOnly), không dựa vào header Authorization:
 * lúc cần refresh thì access token đã hết hạn rồi.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;
  private final RefreshCookie refreshCookie;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED) // 201: đã tạo mới một tài nguyên
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    // @Valid: chạy các annotation trong RegisterRequest. Sai thì không vào tới đây.
    return authService.register(request);
  }

  /**
   * Đăng nhập. Trả 200 (mặc định), không phải 201: đăng nhập không tạo tài nguyên mới.
   * Sai email / mật khẩu → AuthService ném BusinessException 401 → GlobalExceptionHandler trả JSON.
   *
   * [SỬA] Trả ResponseEntity để gắn thêm header Set-Cookie (refresh token). Body giữ nguyên LoginResponse.
   */
  // [SỬA] Đọc thêm header User-Agent để màn hình "Thiết bị đang đăng nhập" hiện "Chrome · Windows".
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return withRefreshCookie(authService.login(request, userAgent));
  }

  /**
   * [THÊM] Access token hết hạn → FE gọi API này (trình duyệt tự gửi kèm cookie) để lấy cặp token mới.
   *
   * required = false: thiếu cookie thì để service trả 401 REFRESH_INVALID cùng dạng JSON với mọi lỗi khác,
   * thay vì Spring tự trả 400 "Missing cookie".
   */
  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
    return withRefreshCookie(authService.refresh(refreshToken));
  }

  /** [THÊM] Đăng xuất: thu hồi phiên phía server + xóa cookie. Luôn 204, kể cả khi không có cookie. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
    authService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
        .build();
  }

  private ResponseEntity<LoginResponse> withRefreshCookie(AuthTokens tokens) {
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.set(tokens.refreshToken()))
        .body(tokens.body());
  }
}
