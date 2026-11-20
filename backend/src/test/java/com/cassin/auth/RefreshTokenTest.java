package com.cassin.auth;

import static com.cassin.auth.AuthFixtures.CHROME_WINDOWS;
import static com.cassin.auth.AuthFixtures.FAMILY;
import static com.cassin.auth.AuthFixtures.NOW;
import static com.cassin.auth.AuthFixtures.TTL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.user.User;

/** Entity RefreshToken: cấp token, hết hạn, "vừa bị thu hồi". Không cần Spring hay DB. */
class RefreshTokenTest {
  private final User user = AuthFixtures.user();

  @Test
  void issue_setsOwnerFamilyAndExpiry_notRevoked() {
    RefreshToken token = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW, TTL, CHROME_WINDOWS, NOW);

    assertThat(token.getUser()).isSameAs(user);
    assertThat(token.getTokenHash()).isEqualTo("a".repeat(64));
    assertThat(token.getFamilyId()).isEqualTo(FAMILY);
    assertThat(token.getCreatedAt()).isEqualTo(NOW);
    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(TTL));
    assertThat(token.getRevokedAt()).isNull();
    assertThat(token.isRevoked()).isFalse();
    assertThat(token.getUserAgent()).isEqualTo(CHROME_WINDOWS);   // [THÊM] V16
    assertThat(token.getSignedInAt()).isEqualTo(NOW);
  }

  // [THÊM] V16: header User-Agent không giới hạn độ dài, cột chỉ 255 ký tự
  @Test
  void issue_userAgent_isTrimmedAndCutTo255_blankBecomesNull() {
    RefreshToken longUa = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW, TTL, "  " + "x".repeat(300) + " ", NOW);
    RefreshToken blankUa = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW, TTL, "   ", NOW);
    RefreshToken noUa = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW, TTL, null, NOW);

    assertThat(longUa.getUserAgent()).hasSize(255).isEqualTo("x".repeat(255));
    assertThat(blankUa.getUserAgent()).isNull();
    assertThat(noUa.getUserAgent()).isNull();
  }

  @Test
  void isExpired_exactlyAtExpiry_isExpired() {
    RefreshToken token = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW, TTL, null, NOW);
    var expiresAt = NOW.plus(TTL);

    assertThat(token.isExpired(expiresAt.minusSeconds(1))).isFalse();
    assertThat(token.isExpired(expiresAt)).isTrue();          // đúng thời điểm hết hạn: KHÔNG còn dùng được
    assertThat(token.isExpired(expiresAt.plusSeconds(1))).isTrue();
  }

  @Test
  void revokedWithin_distinguishesJustRotatedFromLongAgo() {
    RefreshToken token = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW.minus(Duration.ofDays(1)), TTL, null,
        NOW.minus(Duration.ofDays(1)));
    Duration window = Duration.ofSeconds(10);

    assertThat(token.revokedWithin(window, NOW)).isFalse();   // chưa thu hồi

    ReflectionTestUtils.setField(token, "revokedAt", NOW.minusSeconds(3));
    assertThat(token.isRevoked()).isTrue();
    assertThat(token.revokedWithin(window, NOW)).isTrue();    // 3 giây trước: tab khác vừa xoay

    ReflectionTestUtils.setField(token, "revokedAt", NOW.minusSeconds(30));
    assertThat(token.revokedWithin(window, NOW)).isFalse();   // 30 giây trước: không phải chạy đua
  }
}
