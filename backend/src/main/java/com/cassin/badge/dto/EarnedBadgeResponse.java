package com.cassin.badge.dto;

import java.time.Instant;

import com.cassin.badge.BadgeCategory;
import com.cassin.badge.BadgeKind;

/** GET /api/badges/unseen: một CẤP vừa đạt, chưa xem popup. Khớp EarnedBadge ở frontend. */
public record EarnedBadgeResponse(
    String code,
    BadgeKind kind,
    String name,
    String description,
    String unit,
    BadgeCategory category,
    int level,
    int target,
    int maxLevel,
    Instant earnedAt) {
}
