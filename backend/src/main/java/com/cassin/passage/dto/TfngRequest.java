package com.cassin.passage.dto;

import java.util.List;

import com.cassin.passage.TfngAnswer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TfngRequest(
    @NotBlank(message = "Vui lòng nhập câu nhận định") String prompt,
    @NotBlank(message = "Vui lòng chọn câu bằng chứng trong bài") String evidence,
    @NotBlank(message = "Vui lòng nhập lời giải thích") String explanation,
    @Size(max = 3, message = "Mỗi câu tối đa 3 gợi ý") List<@Valid @NotNull HintRequest> hints,
    @NotNull(message = "Vui lòng chọn True / False / Not Given") TfngAnswer answer) implements QuestionRequest {
}
