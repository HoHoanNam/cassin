package com.cassin.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cassin.auth.dto.RevokedSessionsResponse;
import com.cassin.auth.dto.SessionResponse;

import lombok.RequiredArgsConstructor;

/**
 * [THÊM] "Thiết bị đang đăng nhập" ở trang Tài khoản.
 *
 *   GET    /api/auth/sessions              danh sách thiết bị, đánh dấu thiết bị hiện tại
 *   DELETE /api/auth/sessions/{familyId}   đăng xuất một thiết bị
 *   DELETE /api/auth/sessions              đăng xuất mọi thiết bị KHÁC
 *
 * Vì sao nằm dưới /api/auth thay vì /api/me: cookie refresh có Path=/api/auth, trình duyệt CHỈ gửi cookie
 * tới đường dẫn đó. Không có cookie thì không biết đâu là "thiết bị này".
 * Cần CẢ HAI: Bearer access token (biết user là ai, SecurityConfig bắt buộc) + cookie (biết thiết bị nào).
 *
 * Thu hồi phiên chặn được refresh NGAY, nhưng access token đã cấp cho thiết bị đó vẫn dùng được
 * tới khi hết hạn (tối đa 15 phút): đánh đổi của JWT stateless, giống đăng xuất.
 */
@RestController
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

  private final RefreshTokenService refreshTokenService;
  private final RefreshCookie refreshCookie;

  @GetMapping
  public List<SessionResponse> list(
      @AuthenticationPrincipal AuthUser user,
      @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
    return refreshTokenService.listSessions(user.id(), refreshToken);
  }

  /** 204. Đăng xuất chính thiết bị đang dùng → xóa luôn cookie (giống POST /api/auth/logout). */
  @DeleteMapping("/{familyId}")
  public ResponseEntity<Void> revoke(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable UUID familyId,
      @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
    boolean current = refreshTokenService.revokeSession(user.id(), familyId, refreshToken);
    return current
        ? ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear()).build()
        : ResponseEntity.noContent().build();
  }

  @DeleteMapping
  public RevokedSessionsResponse revokeOthers(
      @AuthenticationPrincipal AuthUser user,
      @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
    return new RevokedSessionsResponse(refreshTokenService.revokeOtherSessions(user.id(), refreshToken));
  }
}
