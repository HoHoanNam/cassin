package com.cassin.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cassin.auth.AuthUser;
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

  // [LƯU Ý]: JWT là stateless, nên sau khi đổi mật khẩu, token cũ ở thiết bị khác
  // vẫn dùng được đến khi hết hạn (tối đa 1 giờ). Như vậy là chấp nhận được ở
  // giai đoạn này. Nếu sau này cần thu hồi token ngay, có thể thêm cột
  // password_changed_at và từ chối token phát hành trước mốc đó.
  @PutMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal AuthUser user,
      @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(user.id(), request);
  }
}
