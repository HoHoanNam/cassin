package com.cassin.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cassin.auth.AuthUser;
import com.cassin.common.BusinessException;
import com.cassin.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

/**
 * API của người đang đăng nhập. Cần token (luật anyRequest().authenticated() trong SecurityConfig).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
  private final UserRepository userRepository;

  /**
   * GET /api/me: thông tin của chính mình.
   * Gọi được API này với token = cả luồng JWT đã chạy đúng từ đầu tới cuối.
   *
   * @AuthenticationPrincipal: Spring lấy principal mà JwtAuthenticationFilter đã ghi vào
   * SecurityContext (chính là AuthUser) và truyền vào tham số. Controller không cần tự đọc token.
   */
  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
    // Đọc lại từ DB thay vì trả thẳng dữ liệu trong token:
    // tên hiển thị có thể đã đổi sau khi token được cấp, và tài khoản có thể đã bị xóa.
    return userRepository.findById(authUser.id())
        .map(UserResponse::from)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản"));
  }
}
