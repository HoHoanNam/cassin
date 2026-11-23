package com.cassin.badge;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Khóa ghép của badge_tiers. Record: tự có equals/hashCode theo cả 2 field (giống StudyDayId). */
@Embeddable
public record BadgeTierId(
    @Column(name = "badge_code", length = 40) String badgeCode,
    @Column(name = "level") short level) {
}