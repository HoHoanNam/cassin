package com.cassin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.dto.LoginRequest;
import com.cassin.auth.dto.LoginResponse;
import com.cassin.auth.dto.RegisterRequest;
import com.cassin.user.dto.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Các API công khai: ai cũng gọi được, không cần token
 * (SecurityConfig mở toàn bộ /api/auth/**).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED) // 201: đã tạo mới một tài nguyên
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    // @Valid: chạy các annotation trong RegisterRequest. Sai thì không vào tới đây.
    return authService.register(request);
  }

  /**
   * [THÊM] Đăng nhập. Trả 200 (mặc định), không phải 201: đăng nhập không tạo tài nguyên mới.
   * Sai email / mật khẩu → AuthService ném BusinessException 401 → GlobalExceptionHandler trả JSON.
   */
  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }
}
