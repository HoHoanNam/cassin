package com.cassin.auth;

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
    RefreshToken token = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW, TTL);

    assertThat(token.getUser()).isSameAs(user);
    assertThat(token.getTokenHash()).isEqualTo("a".repeat(64));
    assertThat(token.getFamilyId()).isEqualTo(FAMILY);
    assertThat(token.getCreatedAt()).isEqualTo(NOW);
    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(TTL));
    assertThat(token.getRevokedAt()).isNull();
    assertThat(token.isRevoked()).isFalse();
  }

  @Test
  void isExpired_exactlyAtExpiry_isExpired() {
    RefreshToken token = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW, TTL);
    var expiresAt = NOW.plus(TTL);

    assertThat(token.isExpired(expiresAt.minusSeconds(1))).isFalse();
    assertThat(token.isExpired(expiresAt)).isTrue();          // đúng thời điểm hết hạn: KHÔNG còn dùng được
    assertThat(token.isExpired(expiresAt.plusSeconds(1))).isTrue();
  }

  @Test
  void revokedWithin_distinguishesJustRotatedFromLongAgo() {
    RefreshToken token = RefreshToken.issue(user, "a".repeat(64), FAMILY, NOW.minus(Duration.ofDays(1)), TTL);
    Duration window = Duration.ofSeconds(10);

    assertThat(token.revokedWithin(window, NOW)).isFalse();   // chưa thu hồi

    ReflectionTestUtils.setField(token, "revokedAt", NOW.minusSeconds(3));
    assertThat(token.isRevoked()).isTrue();
    assertThat(token.revokedWithin(window, NOW)).isTrue();    // 3 giây trước: tab khác vừa xoay

    ReflectionTestUtils.setField(token, "revokedAt", NOW.minusSeconds(30));
    assertThat(token.revokedWithin(window, NOW)).isFalse();   // 30 giây trước: không phải chạy đua
  }
}
