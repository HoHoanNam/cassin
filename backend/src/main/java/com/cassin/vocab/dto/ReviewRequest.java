package com.cassin.vocab.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Dùng Boolean (object) chứ không dùng boolean (kiểu nguyên thủy):
// client quên gửi "remembered" thì boolean tự thành false → lặng lẽ chấm "Quên" oan.
// Với Boolean, giá trị thiếu là null → @NotNull bắt được → trả 400.
public record ReviewRequest(@NotNull(message = "Vui lòng chọn Nhớ hoặc Quên") Boolean remembered,
    // [THÊM] Thời gian từ lúc hiện thẻ đến lúc chấm. Không gửi → null → service coi là 0.
    @Min(value = 0, message = "Thời gian không hợp lệ") @Max(value = 600_000, message = "Thời gian không hợp lệ") Integer durationMs) {
}
