package com.cassin.badge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SeenBadgeItem(
    @NotBlank @Size(max = 40) String code,
    @Min(1) @Max(3) int level) {
}
