package com.cassin.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.user.User;

/**
 * Dữ liệu mẫu dùng chung cho test module auth.
 *
 *   NOW      16/11/2026 10:00 giờ VN
 *   người học id 1 "Nam"
 *   RAW      một refresh token gốc (như trong cookie), FAMILY là họ của nó
 */
final class AuthFixtures {
  static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");
  static final Duration TTL = Duration.ofDays(14);
  static final Long USER_ID = 1L;
  static final String RAW = "cookie-refresh-token-value";
  static final UUID FAMILY = UUID.fromString("7d4c2a10-0000-4000-8000-000000000001");

  private AuthFixtures() {
  }

  static User user() {
    User user = new User("nam@gmail.com", "$2a$10$hash", "Nam");
    ReflectionTestUtils.setField(user, "id", USER_ID);
    return user;
  }

  /** Token của RAW, cấp 1 ngày trước NOW (còn hạn 13 ngày), đã có id như vừa đọc từ DB. */
  static RefreshToken activeToken(User owner) {
    RefreshToken token = RefreshToken.issue(owner, RefreshTokenService.hash(RAW), FAMILY,
        NOW.minus(Duration.ofDays(1)), TTL);
    ReflectionTestUtils.setField(token, "id", 10L);
    return token;
  }

  static RefreshToken revokedToken(User owner, Duration revokedAgo) {
    RefreshToken token = activeToken(owner);
    ReflectionTestUtils.setField(token, "revokedAt", NOW.minus(revokedAgo));
    return token;
  }

  static RefreshToken expiredToken(User owner) {
    RefreshToken token = RefreshToken.issue(owner, RefreshTokenService.hash(RAW), FAMILY,
        NOW.minus(TTL).minusSeconds(1), TTL);
    ReflectionTestUtils.setField(token, "id", 10L);
    return token;
  }
}
