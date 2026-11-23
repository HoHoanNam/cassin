package com.cassin.badge;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_badges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBadge {
  @EmbeddedId
  private UserBadgeId id;

  @Column(name = "earned_at", nullable = false, updatable = false)
  private Instant earnedAt;

  @Column(name = "seen_at")
  private Instant seenAt;

  public String badgeCode() {
    return id.badgeCode();
  }

  public int level() {
    return id.level();
  }
}
