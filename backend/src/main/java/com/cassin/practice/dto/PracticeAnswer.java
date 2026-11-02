package com.cassin.practice.dto;

import com.cassin.practice.PracticeMode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bài làm một câu luyện tập (POST /api/practice/answers).
 *
 * @param answer     Fill blank: từ đã gõ. Sentence build: các mảnh ghép lại cách nhau 1 dấu cách.
 * @param durationMs thời gian làm câu này (tối đa 10 phút, quá thì là dữ liệu lạ)
 */
public record PracticeAnswer(
    @NotNull(message = "Thiếu senseId") Long senseId,
    @NotNull(message = "Thiếu exampleIndex") @Min(value = 0, message = "exampleIndex không hợp lệ") Integer exampleIndex,
    @NotNull(message = "Thiếu chế độ luyện") PracticeMode mode,
    @NotNull(message = "Vui lòng nhập câu trả lời") @Size(max = 500, message = "Câu trả lời tối đa 500 ký tự") String answer,
    @Min(value = 0, message = "Thời gian không hợp lệ") @Max(value = 600_000, message = "Thời gian không hợp lệ") int durationMs
) {
}
