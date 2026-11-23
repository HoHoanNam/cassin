package com.cassin.badge.dto;

import java.util.List;

import com.cassin.badge.BadgeCategory;
import com.cassin.badge.BadgeKind;

/**
 * GET /api/badges. Khớp interface Badge ở frontend (core/models/badge.models.ts).
 *   level    : cấp cao nhất đã đạt, 0 = chưa đạt
 *   progress : giá trị hiện tại của chỉ số (chưa chặn trên)
 * Huy hiệu bí mật chưa đạt: name "???", description câu gợi ý, unit "", progress 0, target 1.
 */
public record BadgeResponse(
    String code,
    BadgeKind kind,
    String name,
    String description,
    String unit,
    BadgeCategory category,
    boolean secret,
    int level,
    long progress,
    List<TierResponse> tiers) {
}
