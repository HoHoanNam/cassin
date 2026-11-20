package com.cassin.passage.dto;

import java.util.List;

import com.cassin.grammar.CefrLevel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin tạo / sửa CẢ bài đọc lẫn bộ câu hỏi trong một request (PUT thay toàn bộ). Bài nháp được phép chưa có câu nào. */
public record PassageRequest(
    @NotBlank(message = "Vui lòng nhập tiêu đề") @Size(max = 150, message = "Tiêu đề tối đa 150 ký tự") String title,
    @NotBlank(message = "Vui lòng nhập nội dung bài đọc") @Size(max = 5000, message = "Bài đọc tối đa 5000 ký tự") String content,
    @NotNull(message = "Vui lòng chọn trình độ") CefrLevel level,
    @NotNull @Size(max = 20, message = "Mỗi bài tối đa 20 câu hỏi") List<@Valid @NotNull QuestionRequest> questions) {
}
