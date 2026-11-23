package com.cassin.badge;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Khóa ghép của user_badges: (người, huy hiệu, cấp). */
@Embeddable
public record UserBadgeId(
    @Column(name = "user_id") Long userId,
    @Column(name = "badge_code", length = 40) String badgeCode,
    @Column(name = "level") short level) {
}