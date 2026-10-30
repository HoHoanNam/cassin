package com.cassin.grammar.dto;

import com.cassin.grammar.CefrLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TopicRequest(
    @NotBlank(message = "Vui lòng nhập tiêu đề") @Size(max = 150) String title,
    @NotNull(message = "Vui lòng chọn trình độ") CefrLevel level,
    @NotBlank(message = "Vui lòng nhập bài giải thích") String explanation) {
}
