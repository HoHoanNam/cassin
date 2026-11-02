package com.cassin.practice.dto;

/**
 * Kết quả chấm một câu luyện tập.
 *
 * @param expected đáp án đúng (Fill blank: từ; Sentence build: cả câu) để giao diện hiện lại
 * @param hint     gợi ý khi sai "gần đúng", vd gõ "improve" cho chỗ cần "improved". null nếu không có.
 */
public record PracticeResult(boolean correct, String expected, String hint) {
}
