package com.cassin.badge.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/badges/seen. Tối đa 50 mục (popup lần lượt vài cái; chặn body khổng lồ). */
public record MarkSeenRequest(
    @NotNull @Size(max = 50) List<@Valid SeenBadgeItem> items) {
}
