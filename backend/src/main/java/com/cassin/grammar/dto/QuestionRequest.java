package com.cassin.grammar.dto;

import java.util.List;

import com.cassin.grammar.OptionInput;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
    @NotBlank(message = "Vui lòng nhập câu hỏi") String content,
    @NotBlank(message = "Vui lòng nhập lời giải thích") String explanation,
    @NotNull @Size(min = 4, max = 4, message = "Câu hỏi phải có đúng 4 đáp án") List<@Valid @NotNull OptionRequest> options) {

  // Đổi DTO (có annotation validate) → OptionInput (record domain, không dính
  // annotation web).
  public List<OptionInput> toInputs() {
    return options.stream().map(o -> new OptionInput(o.content(), o.correct())).toList();
  }
}
