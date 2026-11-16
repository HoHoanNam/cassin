package com.cassin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.cassin.auth.dto.RefreshTokenProperties;

/** Thuộc tính bảo mật của cookie refresh token (giá trị header Set-Cookie). */
class RefreshCookieTest {
  private static final Duration TTL = Duration.ofDays(14);

  @Test
  void set_isHttpOnlyStrictScopedToAuthApi() {
    String header = new RefreshCookie(new RefreshTokenProperties(TTL, true)).set("abc");

    assertThat(header)
        .startsWith(RefreshCookie.NAME + "=abc")
        .contains("Path=/api/auth")
        .contains("Max-Age=" + TTL.toSeconds())   // 1209600
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Secure");
  }

  @Test
  void set_withoutSecureFlag_forLocalHttpDev() {
    String header = new RefreshCookie(new RefreshTokenProperties(TTL, false)).set("abc");

    assertThat(header).doesNotContain("Secure");
  }

  @Test
  void clear_expiresCookieImmediately_onSamePath() {
    String header = new RefreshCookie(new RefreshTokenProperties(TTL, false)).clear();

    assertThat(header)
        .startsWith(RefreshCookie.NAME + "=;")
        .contains("Path=/api/auth")    // khác path thì trình duyệt coi là cookie khác, không xóa được
        .contains("Max-Age=0");
  }
}
