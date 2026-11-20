package com.cassin.user;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cassin.auth.AuthUser;
import com.cassin.auth.RefreshCookie;
import com.cassin.user.dto.ChangePasswordRequest;
import com.cassin.user.dto.UpdateProfileRequest;
import com.cassin.user.dto.UserResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * API của người đang đăng nhập. Cần token (luật anyRequest().authenticated()
 * trong SecurityConfig).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
  // [SỬA] Bỏ UserRepository: controller chỉ nói chuyện với service (xem UserService.getMe).
  private final UserService userService;
  // [THÊM] Đặt cookie refresh token mới sau khi đổi mật khẩu
  private final RefreshCookie refreshCookie;

  /**
   * GET /api/me: thông tin của chính mình.
   * Gọi được API này với token = cả luồng JWT đã chạy đúng từ đầu tới cuối.
   *
   * @AuthenticationPrincipal: Spring lấy principal mà JwtAuthenticationFilter đã
   *                           ghi vào
   *                           SecurityContext (chính là AuthUser) và truyền vào
   *                           tham số. Controller không cần tự đọc token.
   */
  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
    // Đọc lại từ DB thay vì trả thẳng dữ liệu trong token:
    // tên hiển thị có thể đã đổi sau khi token được cấp, và tài khoản có thể đã bị
    // xóa.
    return userService.getMe(authUser.id());
  }

  @PutMapping("/me")
  public UserResponse updateProfile(
      @AuthenticationPrincipal AuthUser user,
      @Valid @RequestBody UpdateProfileRequest request) {
    return userService.updateProfile(user.id(), request);
  }

  /**
   * [SỬA] Đổi mật khẩu xong thì mọi phiên đăng nhập khác bị thu hồi (xem UserService.changePassword).
   * Thiết bị đang đổi nhận refresh token MỚI qua cookie nên không bị đăng xuất.
   * Cookie có Path=/api/auth: response của /api/me/password vẫn đặt được cookie cho path đó.
   */
  @PutMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthUser user,
      @Valid @RequestBody ChangePasswordRequest request,
      // [THÊM] phiên mới mở cho thiết bị đang đổi mật khẩu cũng ghi lại thiết bị
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    String refreshToken = userService.changePassword(user.id(), request, userAgent);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.set(refreshToken))
        .build();
  }
}
