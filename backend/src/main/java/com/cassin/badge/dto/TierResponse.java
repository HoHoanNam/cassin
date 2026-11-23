package com.cassin.badge.dto;

import java.time.Instant;

/** Một cấp trong BadgeResponse. earnedAt = null: chưa đạt. */
public record TierResponse(int level, int target, Instant earnedAt) {
}
