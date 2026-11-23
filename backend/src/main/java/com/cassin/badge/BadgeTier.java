package com.cassin.badge;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một cấp của huy hiệu: phải đạt >= target thì được cấp. Chỉ đọc, seed trong V18. */
@Entity
@Table(name = "badge_tiers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BadgeTier {

  @EmbeddedId
  private BadgeTierId id;

  @Column(nullable = false)
  private int target;

  public String badgeCode() {
    return id.badgeCode();
  }

  public int level() {
    return id.level();
  }
}