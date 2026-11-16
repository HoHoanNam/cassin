package com.cassin.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.cassin.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
/**
 * [THÊM] Một refresh token đã cấp. Chỉ lưu SHA-256 của token, KHÔNG lưu token gốc.
 *
 *   đăng nhập ──▶ token #1 (họ F)
 *   refresh   ──▶ #1 bị thu hồi, cấp #2 (cùng họ F)
 *   ai đó dùng lại #1 (đã thu hồi) ──▶ thu hồi CẢ HỌ F (#2 cũng chết) → phải đăng nhập lại
 *
 * Việc THU HỒI đi qua câu UPDATE có điều kiện trong RefreshTokenRepository, không qua entity.
 */
@Getter
// [SỬA] @NoArgsConstructor → access = PROTECTED: JPA cần constructor rỗng, nhưng code bên ngoài
// không được tự "new RefreshToken()" rồi bỏ qua issue() (sẽ ra token thiếu user / hash / hạn).
// Cùng cách với Deck, Word, ContentReport.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  static RefreshToken issue(User user, String tokenHash, UUID familyId, Instant now, Duration ttl) {
    RefreshToken token = new RefreshToken();
    token.user = user;
    token.tokenHash = tokenHash;
    token.familyId = familyId;
    token.createdAt = now;
    token.expiresAt = now.plus(ttl);
    return token;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public boolean revokedWithin(Duration window, Instant now) {
    return revokedAt != null && revokedAt.isAfter(now.minus(window));
  }
}
