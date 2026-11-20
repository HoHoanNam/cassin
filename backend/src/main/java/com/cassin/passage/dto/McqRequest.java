package com.cassin.passage.dto;

import java.util.List;

import com.cassin.passage.ChoiceInput;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record McqRequest(
    @NotBlank(message = "Vui lòng nhập câu hỏi") String prompt,
    @NotBlank(message = "Vui lòng chọn câu bằng chứng trong bài") String evidence,
    @NotBlank(message = "Vui lòng nhập lời giải thích") String explanation,
    @Size(max = 3, message = "Mỗi câu tối đa 3 gợi ý") List<@Valid @NotNull HintRequest> hints,
    @NotNull @Size(min = 4, max = 4, message = "Câu trắc nghiệm phải có đúng 4 lựa chọn")
    List<@Valid @NotNull OptionRequest> options) implements QuestionRequest {

  public List<ChoiceInput> toChoices() {
    return options.stream().map(o -> new ChoiceInput(o.content(), o.correct())).toList();
  }
}
